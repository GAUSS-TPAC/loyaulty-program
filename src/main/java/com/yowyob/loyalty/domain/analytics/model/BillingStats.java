package com.yowyob.loyalty.domain.analytics.model;

import java.math.BigDecimal;

/**
 * Facturation des abonnements sur la fenêtre, pour une devise.
 *
 * <p>À ne pas confondre avec {@link GatewayStats} : ici il s'agit de ce que la
 * plateforme facture à ses tenants (revenu SaaS), là de ce que les membres rechargent
 * sur leur wallet (flux transitant par la passerelle). Les deux sont des « paiements »
 * mais ne se somment pas.
 */
public record BillingStats(
        String currency,
        long issued,
        long paid,
        long pending,
        long failed,
        BigDecimal billedAmount,
        BigDecimal collectedAmount
) {

    public static BillingStats empty(String currency) {
        return new BillingStats(currency, 0, 0, 0, 0, BigDecimal.ZERO, BigDecimal.ZERO);
    }

    /** Part du facturé effectivement recouvré — l'indicateur de recouvrement. */
    public double collectionRate() {
        if (billedAmount.signum() == 0) {
            return 0d;
        }
        return collectedAmount.doubleValue() / billedAmount.doubleValue();
    }

    /** Reste à recouvrer : factures émises mais ni payées ni abandonnées. */
    public BigDecimal outstandingAmount() {
        return billedAmount.subtract(collectedAmount).max(BigDecimal.ZERO);
    }
}
