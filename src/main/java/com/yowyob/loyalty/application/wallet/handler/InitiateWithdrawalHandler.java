package com.yowyob.loyalty.application.wallet.handler;

import com.yowyob.loyalty.domain.shared.model.TenantId;
import com.yowyob.loyalty.domain.shared.model.UserId;
import com.yowyob.loyalty.domain.wallet.exception.WalletDomainException;
import com.yowyob.loyalty.domain.wallet.model.PaymentDirection;
import com.yowyob.loyalty.domain.wallet.model.PaymentInitiationResult;
import com.yowyob.loyalty.domain.wallet.model.PaymentRequest;
import com.yowyob.loyalty.domain.wallet.model.PaymentStatus;
import com.yowyob.loyalty.domain.wallet.model.TransactionSource;
import com.yowyob.loyalty.domain.wallet.port.in.InitiateWithdrawalUseCase;
import com.yowyob.loyalty.domain.wallet.port.out.IdempotencyPort;
import com.yowyob.loyalty.domain.wallet.port.out.PaymentRequestRepository;
import com.yowyob.loyalty.domain.wallet.service.WalletDomainService;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@Service
public class InitiateWithdrawalHandler implements InitiateWithdrawalUseCase {

    private static final Duration PAYMENT_EXPIRY = Duration.ofMinutes(15);

    private final WalletDomainService domainService;
    private final PaymentRequestRepository paymentRequestRepo;
    private final IdempotencyPort idempotency;

    public InitiateWithdrawalHandler(WalletDomainService domainService, PaymentRequestRepository paymentRequestRepo, IdempotencyPort idempotency) {
        this.domainService = domainService;
        this.paymentRequestRepo = paymentRequestRepo;
        this.idempotency = idempotency;
    }

    @Override
    public Mono<PaymentInitiationResult> initiateWithdrawal(TenantId tenantId, UserId memberId, BigDecimal amount, String targetAccount, String provider, String idempotencyKey) {
        String description = "Retrait vers " + targetAccount;
        return domainService.debit(tenantId, memberId, amount, description, null, idempotencyKey)
            .flatMap(result -> {
                if (result.otpRequired()) {
                    return Mono.error(new WalletDomainException("OTP requis pour ce retrait — utilisez le flow de confirmation OTP"));
                }
                // Kernel Core n'expose aucun endpoint de décaissement : le retrait est tracé
                // ici et reste à exécuter hors passerelle, d'où une référence locale et
                // l'absence d'URL de redirection.
                String externalRef = UUID.randomUUID().toString();
                Instant now = Instant.now();
                Instant expiresAt = now.plus(PAYMENT_EXPIRY);
                PaymentRequest paymentRequest = new PaymentRequest(
                    UUID.randomUUID(),
                    tenantId,
                    result.updatedWallet().getId(),
                    memberId,
                    null,
                    externalRef,
                    provider,
                    null,
                    PaymentDirection.OUTBOUND,
                    amount,
                    result.updatedWallet().getCurrencyCode(),
                    targetAccount,
                    null,
                    null,
                    idempotencyKey,
                    PaymentStatus.PENDING,
                    null,
                    now,
                    null,
                    expiresAt
                );
                return paymentRequestRepo.save(paymentRequest)
                    .thenReturn(new PaymentInitiationResult(
                        externalRef,
                        PaymentStatus.PENDING.name(),
                        null,
                        null,
                        expiresAt,
                        false
                    ));
            });
    }
}
