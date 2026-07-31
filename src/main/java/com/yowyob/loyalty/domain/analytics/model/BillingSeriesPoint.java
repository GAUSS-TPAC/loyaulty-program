package com.yowyob.loyalty.domain.analytics.model;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Un point de la courbe de revenu SaaS.
 *
 * <p>Deux montants volontairement distincts : {@code billedAmount} date les factures
 * sur leur émission, {@code collectedAmount} sur leur encaissement réel. Une facture
 * émise en janvier et payée en mars apparaît donc dans deux tranches différentes —
 * c'est précisément l'écart que la courbe doit rendre visible.
 */
public record BillingSeriesPoint(
        Instant bucket,
        String currency,
        long issued,
        long paid,
        BigDecimal billedAmount,
        BigDecimal collectedAmount
) {}
