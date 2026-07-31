package com.yowyob.loyalty.api.wallet.dto.response;

import com.yowyob.loyalty.domain.wallet.model.PaymentRequest;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * @param gatewayStatus libellé brut de la passerelle, exposé tel quel pour le support
 */
public record TopUpStatusResponse(
    String reference,
    String status,
    String gatewayStatus,
    BigDecimal amount,
    String currency,
    String provider,
    String method,
    String redirectUrl,
    Instant initiatedAt,
    Instant confirmedAt,
    Instant expiresAt
) {
    public static TopUpStatusResponse from(PaymentRequest request) {
        return new TopUpStatusResponse(
                request.externalRef(),
                request.status().name(),
                request.rawStatus(),
                request.amount(),
                request.currency(),
                request.provider(),
                request.method(),
                request.redirectUrl(),
                request.initiatedAt(),
                request.confirmedAt(),
                request.expiresAt());
    }
}
