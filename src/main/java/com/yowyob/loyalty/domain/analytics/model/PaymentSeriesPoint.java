package com.yowyob.loyalty.domain.analytics.model;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Un point de la courbe d'encaissement.
 *
 * <p>Les tentatives sont datées sur {@code initiated_at} et le montant encaissé sur
 * ces mêmes tentatives : une recharge lancée le lundi et confirmée le mardi reste
 * comptée au lundi. C'est ce qui permet de lire le taux de succès directement sur la
 * courbe (succeeded / attempts d'une même tranche) sans mélanger deux cohortes.
 */
public record PaymentSeriesPoint(
        Instant bucket,
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
