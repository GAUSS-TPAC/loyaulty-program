package com.yowyob.loyalty.domain.analytics.model;

import java.math.BigDecimal;

/**
 * Santé de la passerelle d'encaissement (recharges wallet déléguées à Kernel Core)
 * sur la fenêtre observée, pour une devise.
 *
 * <p>Une ligne par devise : {@code payment_requests} n'impose pas une devise unique,
 * et un total inter-devises serait faux. Les compteurs, eux, sont additionnables.
 *
 * <p>{@code pending} regroupe INITIATED et PENDING : côté exploitation, ce qui compte
 * est « toujours en attente d'un verdict », pas la nuance entre les deux.
 */
public record GatewayStats(
        String currency,
        long initiated,
        long succeeded,
        long failed,
        long pending,
        BigDecimal collectedAmount,
        BigDecimal attemptedAmount
) {

    public static GatewayStats empty(String currency) {
        return new GatewayStats(currency, 0, 0, 0, 0, BigDecimal.ZERO, BigDecimal.ZERO);
    }

    /**
     * Taux de succès rapporté aux seules tentatives tranchées : inclure les recharges
     * encore en attente ferait chuter artificiellement le taux en cours de journée.
     */
    public double successRate() {
        long settled = succeeded + failed;
        if (settled == 0) {
            return 0d;
        }
        return (double) succeeded / settled;
    }

    /** Panier moyen encaissé, sur les seules recharges effectivement abouties. */
    public BigDecimal averageTicket() {
        if (succeeded == 0) {
            return BigDecimal.ZERO;
        }
        return collectedAmount.divide(BigDecimal.valueOf(succeeded), 2, java.math.RoundingMode.HALF_UP);
    }
}
