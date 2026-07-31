package com.yowyob.loyalty.domain.wallet.model;

import com.yowyob.loyalty.domain.shared.model.TenantId;
import com.yowyob.loyalty.domain.shared.model.UserId;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Trace locale d'un ordre de paiement délégué à la passerelle Kernel Core.
 *
 * <p>C'est la table de rapprochement : {@code externalRef} porte l'identifiant de la
 * PaymentOrder Kernel Core, seul lien entre un callback entrant et le wallet à créditer.
 * Le tenant et le membre y sont dupliqués parce qu'un callback arrive sans JWT, donc sans
 * contexte de tenant à résoudre.
 */
public record PaymentRequest(
    UUID id,
    TenantId tenantId,
    UUID walletId,
    UserId memberId,
    UUID walletTransactionId,
    String externalRef,
    String provider,
    String method,
    PaymentDirection direction,
    BigDecimal amount,
    String currency,
    String payerReference,
    String providerReference,
    String redirectUrl,
    String idempotencyKey,
    PaymentStatus status,
    String rawStatus,
    Instant initiatedAt,
    Instant confirmedAt,
    Instant expiresAt
) {

    /** Reporte l'état renvoyé par la passerelle, sans jamais écraser une valeur connue par un null. */
    public PaymentRequest withGatewayState(PaymentOrder order, Instant now) {
        return new PaymentRequest(
            id, tenantId, walletId, memberId, walletTransactionId, externalRef,
            provider, method, direction, amount, currency, payerReference,
            order.providerReference() != null ? order.providerReference() : providerReference,
            order.redirectUrl() != null ? order.redirectUrl() : redirectUrl,
            idempotencyKey,
            order.status(),
            order.rawStatus(),
            initiatedAt,
            order.status().isSuccessful() && confirmedAt == null ? now : confirmedAt,
            expiresAt
        );
    }

    public PaymentRequest withWalletTransaction(UUID transactionId) {
        return new PaymentRequest(
            id, tenantId, walletId, memberId, transactionId, externalRef,
            provider, method, direction, amount, currency, payerReference,
            providerReference, redirectUrl, idempotencyKey, status, rawStatus,
            initiatedAt, confirmedAt, expiresAt
        );
    }

    public boolean isFinal() {
        return status.isFinal();
    }
}
