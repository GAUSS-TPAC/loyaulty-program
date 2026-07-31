package com.yowyob.loyalty.application.wallet.handler;

import com.yowyob.loyalty.domain.wallet.exception.WalletDomainException;
import com.yowyob.loyalty.domain.wallet.model.PaymentDirection;
import com.yowyob.loyalty.domain.wallet.model.PaymentRequest;
import com.yowyob.loyalty.domain.wallet.model.TransactionSource;
import com.yowyob.loyalty.domain.wallet.port.in.ConfirmTopUpUseCase;
import com.yowyob.loyalty.domain.wallet.port.in.CreditWalletUseCase;
import com.yowyob.loyalty.domain.wallet.port.out.PaymentGatewayPort;
import com.yowyob.loyalty.domain.wallet.port.out.PaymentRequestRepository;
import com.yowyob.loyalty.shared.exception.IdempotencyConflictException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Instant;

/**
 * Réconcilie une recharge avec la passerelle, puis crédite le wallet si l'encaissement est
 * confirmé.
 *
 * <p>Le contenu d'un callback entrant n'est jamais cru sur parole : on rappelle la
 * passerelle ({@code refresh}) et c'est sa réponse qui décide. Un callback forgé ne peut
 * donc pas créditer un wallet.
 *
 * <p>Double garde contre le double crédit : le statut déjà final de la PaymentRequest, et
 * la clé d'idempotence {@code topup-<orderId>} portée par le crédit lui-même.
 */
@Service
public class ConfirmTopUpHandler implements ConfirmTopUpUseCase {

    private static final Logger log = LoggerFactory.getLogger(ConfirmTopUpHandler.class);

    private final PaymentRequestRepository paymentRequestRepo;
    private final PaymentGatewayPort paymentGateway;
    private final CreditWalletUseCase creditWalletUseCase;

    public ConfirmTopUpHandler(PaymentRequestRepository paymentRequestRepo,
                               PaymentGatewayPort paymentGateway,
                               @Qualifier("creditWalletHandler") CreditWalletUseCase creditWalletUseCase) {
        this.paymentRequestRepo = paymentRequestRepo;
        this.paymentGateway = paymentGateway;
        this.creditWalletUseCase = creditWalletUseCase;
    }

    @Override
    public Mono<PaymentRequest> confirm(String externalRef) {
        return paymentRequestRepo.findByExternalRef(externalRef)
                .switchIfEmpty(Mono.error(new WalletDomainException(
                        "Aucune demande de paiement connue pour la référence " + externalRef)))
                .flatMap(request -> {
                    if (request.direction() != PaymentDirection.INBOUND) {
                        return Mono.error(new WalletDomainException(
                                "La référence " + externalRef + " n'est pas une recharge"));
                    }
                    if (request.isFinal()) {
                        return Mono.just(request);
                    }
                    return paymentGateway.refresh(request.tenantId(), externalRef)
                            .map(order -> request.withGatewayState(order, Instant.now()))
                            .flatMap(this::applyState);
                });
    }

    private Mono<PaymentRequest> applyState(PaymentRequest refreshed) {
        if (!refreshed.status().isSuccessful()) {
            return paymentRequestRepo.update(refreshed);
        }
        return creditWalletUseCase.credit(
                        refreshed.tenantId(),
                        refreshed.memberId(),
                        refreshed.amount(),
                        TransactionSource.TOPUP_GATEWAY,
                        refreshed.externalRef(),
                        "topup-" + refreshed.externalRef())
                .thenReturn(refreshed)
                // Le crédit a déjà été passé par un callback concurrent : l'état final est le
                // même, seule la trace locale reste à mettre à jour.
                .onErrorResume(IdempotencyConflictException.class, e -> {
                    log.info("Recharge {} déjà créditée, mise à jour de la trace seulement", refreshed.externalRef());
                    return Mono.just(refreshed);
                })
                .flatMap(paymentRequestRepo::update);
    }
}
