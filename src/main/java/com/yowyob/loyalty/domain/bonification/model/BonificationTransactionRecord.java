package com.yowyob.loyalty.domain.bonification.model;

import com.yowyob.loyalty.domain.shared.model.TenantId;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Trace locale d'une soumission vers l'API partenaire Bonification.
 *
 * <p>Écrite après coup, hors du chemin critique : le flux métier ne la relit jamais,
 * elle n'existe que pour l'analytique (volume, taux de succès, contribution du canal).
 * Les échecs sont journalisés au même titre que les succès — sans eux le taux de
 * succès n'a pas de dénominateur.
 *
 * <p>{@code amount} est un montant nu : l'API partenaire ne déclare aucune devise,
 * il ne doit donc pas être additionné aux montants wallet ou facturation.
 */
public record BonificationTransactionRecord(
        UUID id,
        TenantId tenantId,
        String transactionId,
        String clientLogin,
        BigDecimal amount,
        boolean debit,
        String status,
        boolean succeeded,
        String message,
        Instant submittedAt
) {

    /** Statuts que le partenaire renvoie pour une transaction effectivement enregistrée. */
    private static final String COMPLETED_STATUS = "COMPLETE";

    public static BonificationTransactionRecord of(TenantId tenantId,
                                                    BonificationTransactionResult result,
                                                    Instant submittedAt) {
        return new BonificationTransactionRecord(
                UUID.randomUUID(),
                tenantId,
                result.transactionId(),
                result.clientLogin(),
                BigDecimal.valueOf(result.amount()),
                result.debit(),
                result.status() != null ? result.status() : "UNKNOWN",
                isSuccessful(result),
                result.message(),
                submittedAt
        );
    }

    /**
     * Trace d'une soumission qui n'a jamais abouti (partenaire injoignable, credentials
     * refusés…). Sans elle, un partenaire totalement en panne afficherait 100 % de succès
     * sur zéro transaction plutôt qu'un taux d'échec visible.
     */
    public static BonificationTransactionRecord failed(TenantId tenantId,
                                                        BonificationTransactionRequest request,
                                                        String reason,
                                                        Instant submittedAt) {
        return new BonificationTransactionRecord(
                UUID.randomUUID(),
                tenantId,
                null,
                request.clientLogin(),
                BigDecimal.valueOf(request.amount()),
                request.debit(),
                "ERROR",
                false,
                reason,
                submittedAt
        );
    }

    private static boolean isSuccessful(BonificationTransactionResult result) {
        return result.transactionId() != null
                && COMPLETED_STATUS.equalsIgnoreCase(result.status());
    }
}
