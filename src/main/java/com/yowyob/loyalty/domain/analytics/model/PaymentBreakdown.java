package com.yowyob.loyalty.domain.analytics.model;

import java.math.BigDecimal;

/**
 * Ventilation des encaissements selon une dimension (moyen de paiement, PSP, statut).
 *
 * <p>{@code dimension} nomme l'axe et {@code key} la valeur : un seul type suffit pour
 * toutes les ventilations, et le client peut les afficher dans un composant unique.
 */
public record PaymentBreakdown(
        String dimension,
        String key,
        String currency,
        long attempts,
        long succeeded,
        long failed,
        BigDecimal collectedAmount
) {

    public double successRate() {
        long settled = succeeded + failed;
        if (settled == 0) {
            return 0d;
        }
        return (double) succeeded / settled;
    }
}
