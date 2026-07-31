package com.yowyob.loyalty.api.wallet.dto.response;

import com.yowyob.loyalty.domain.wallet.model.PaymentInitiationResult;

import java.time.Instant;

/**
 * @param reference          identifiant de l'ordre côté passerelle, à réutiliser pour la confirmation
 * @param redirectUrl        page de paiement à ouvrir pour le membre
 * @param requiresUserAction vrai tant que le paiement attend une action du payeur
 */
public record TopUpResponse(
    String reference,
    String status,
    String redirectUrl,
    Instant expiresAt,
    boolean requiresUserAction
) {
    public static TopUpResponse from(PaymentInitiationResult result) {
        return new TopUpResponse(
                result.externalRef(),
                result.status(),
                result.redirectUrl(),
                result.expiresAt(),
                result.requiresUserAction());
    }
}
