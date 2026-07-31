package com.yowyob.loyalty.application.wallet.handler;

import com.yowyob.loyalty.domain.shared.model.TenantId;
import com.yowyob.loyalty.domain.shared.model.UserId;
import com.yowyob.loyalty.domain.wallet.exception.WalletDomainException;
import com.yowyob.loyalty.domain.wallet.model.PaymentDirection;
import com.yowyob.loyalty.domain.wallet.model.PaymentInitiationResult;
import com.yowyob.loyalty.domain.wallet.model.PaymentMethod;
import com.yowyob.loyalty.domain.wallet.model.PaymentOrderRequest;
import com.yowyob.loyalty.domain.wallet.model.PaymentRequest;
import com.yowyob.loyalty.domain.wallet.model.PaymentStatus;
import com.yowyob.loyalty.domain.wallet.model.Wallet;
import com.yowyob.loyalty.domain.wallet.model.WalletPolicy;
import com.yowyob.loyalty.domain.wallet.port.in.InitiateTopUpUseCase;
import com.yowyob.loyalty.domain.wallet.port.out.IdempotencyPort;
import com.yowyob.loyalty.domain.wallet.port.out.PaymentGatewayPort;
import com.yowyob.loyalty.domain.wallet.port.out.PaymentRequestRepository;
import com.yowyob.loyalty.domain.wallet.port.out.WalletPolicyRepository;
import com.yowyob.loyalty.domain.wallet.port.out.WalletRepository;
import com.yowyob.loyalty.shared.exception.IdempotencyConflictException;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/**
 * Ouvre une recharge de wallet auprès de la passerelle de paiement.
 *
 * <p>Le wallet n'est jamais crédité ici : l'argent n'est pas encore encaissé. Le crédit
 * n'a lieu qu'à la confirmation ({@link ConfirmTopUpHandler}), une fois la passerelle
 * ré-interrogée.
 */
@Service
public class InitiateTopUpHandler implements InitiateTopUpUseCase {

    private static final Duration IDEMPOTENCY_TTL = Duration.ofHours(24);
    /** Fenêtre au-delà de laquelle une recharge non confirmée est considérée périmée. */
    private static final Duration PAYMENT_EXPIRY = Duration.ofMinutes(30);

    private final WalletRepository walletRepo;
    private final WalletPolicyRepository policyRepo;
    private final PaymentRequestRepository paymentRequestRepo;
    private final PaymentGatewayPort paymentGateway;
    private final IdempotencyPort idempotency;

    public InitiateTopUpHandler(WalletRepository walletRepo,
                                WalletPolicyRepository policyRepo,
                                PaymentRequestRepository paymentRequestRepo,
                                PaymentGatewayPort paymentGateway,
                                IdempotencyPort idempotency) {
        this.walletRepo = walletRepo;
        this.policyRepo = policyRepo;
        this.paymentRequestRepo = paymentRequestRepo;
        this.paymentGateway = paymentGateway;
        this.idempotency = idempotency;
    }

    @Override
    public Mono<PaymentInitiationResult> initiateTopUp(TenantId tenantId, UserId memberId, BigDecimal amount,
                                                       String provider, PaymentMethod method, String payerReference,
                                                       String idempotencyKey) {
        if (method == PaymentMethod.MOBILE_MONEY && (payerReference == null || payerReference.isBlank())) {
            return Mono.error(new WalletDomainException(
                    "Un numéro Mobile Money (payerReference) est obligatoire pour une recharge MOBILE_MONEY"));
        }

        String effectiveKey = (idempotencyKey == null || idempotencyKey.isBlank())
                ? UUID.randomUUID().toString()
                : idempotencyKey;

        return idempotency.registerIfAbsent(effectiveKey, tenantId.value().toString(), IDEMPOTENCY_TTL, "PROCESSING")
                .flatMap(registered -> registered
                        ? doInitiate(tenantId, memberId, amount, provider, method, payerReference, effectiveKey)
                        : Mono.error(new IdempotencyConflictException(effectiveKey)));
    }

    private Mono<PaymentInitiationResult> doInitiate(TenantId tenantId, UserId memberId, BigDecimal amount,
                                                     String provider, PaymentMethod method, String payerReference,
                                                     String idempotencyKey) {
        return Mono.zip(policyRepo.findByTenant(tenantId), walletRepo.findByMemberAndTenant(memberId, tenantId))
                .switchIfEmpty(Mono.error(new WalletDomainException("Aucun wallet pour ce membre")))
                .flatMap(tuple -> {
                    WalletPolicy policy = tuple.getT1();
                    Wallet wallet = tuple.getT2();

                    // Le plafond est validé avant d'engager le payeur : refuser le crédit après
                    // encaissement obligerait à rembourser, ce que la passerelle n'expose pas.
                    policy.validateCredit(amount, wallet.getBalance())
                            .ifPresent(msg -> { throw new WalletDomainException(msg); });
                    if (!wallet.getStatus().canCredit()) {
                        return Mono.error(new WalletDomainException(
                                "Wallet ne peut pas être rechargé dans l'état " + wallet.getStatus()));
                    }

                    Instant now = Instant.now();
                    PaymentOrderRequest gatewayRequest = new PaymentOrderRequest(
                            tenantId, amount, wallet.getCurrencyCode(), provider, method, payerReference,
                            "Recharge wallet " + wallet.getId(), idempotencyKey);

                    return paymentGateway.initiate(gatewayRequest)
                            .flatMap(order -> {
                                PaymentRequest request = new PaymentRequest(
                                        UUID.randomUUID(),
                                        tenantId,
                                        wallet.getId(),
                                        memberId,
                                        null,
                                        order.id(),
                                        provider,
                                        method.name(),
                                        PaymentDirection.INBOUND,
                                        amount,
                                        wallet.getCurrencyCode(),
                                        payerReference,
                                        order.providerReference(),
                                        order.redirectUrl(),
                                        idempotencyKey,
                                        order.status(),
                                        order.rawStatus(),
                                        now,
                                        null,
                                        now.plus(PAYMENT_EXPIRY));

                                return paymentRequestRepo.save(request)
                                        .map(saved -> new PaymentInitiationResult(
                                                saved.externalRef(),
                                                saved.status().name(),
                                                null,
                                                saved.redirectUrl(),
                                                saved.expiresAt(),
                                                !saved.status().isFinal()));
                            });
                });
    }
}
