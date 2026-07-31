package com.yowyob.loyalty.domain.wallet.port.in;

import com.yowyob.loyalty.domain.wallet.model.PaymentRequest;
import reactor.core.publisher.Mono;

/**
 * Réconcilie une recharge : ré-interroge la passerelle pour l'ordre {@code externalRef},
 * puis crédite le wallet si — et seulement si — la passerelle confirme l'encaissement.
 *
 * <p>Déclenchable par un callback entrant comme par une relance manuelle ; le résultat ne
 * dépend jamais du contenu du callback, seulement de l'état renvoyé par la passerelle.
 */
public interface ConfirmTopUpUseCase {
    Mono<PaymentRequest> confirm(String externalRef);
}
