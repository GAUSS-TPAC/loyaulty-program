package com.yowyob.loyalty.domain.analytics.model;

/**
 * Entonnoir d'abonnement à l'API sur la fenêtre observée.
 *
 * <p>Attention à la lecture : {@code tenant_subscriptions} porte une ligne par tenant,
 * mise à jour sur place (pas de journal d'événements d'abonnement). Les compteurs
 * d'état ({@code activeCount}…) décrivent donc l'état <em>courant</em> des abonnements
 * créés dans la fenêtre, et non le nombre de transitions survenues pendant celle-ci.
 * {@code cancelledInPeriod} fait exception : il s'appuie sur {@code cancelled_at}, un
 * horodatage réellement daté.
 */
public record SubscriptionFunnel(
        long createdInPeriod,
        long trialCount,
        long activeCount,
        long pastDueCount,
        long cancelledCount,
        long expiredCount,
        long cancelledInPeriod
) {

    public static SubscriptionFunnel empty() {
        return new SubscriptionFunnel(0, 0, 0, 0, 0, 0, 0);
    }

    /**
     * Part des abonnements créés dans la fenêtre qui sont aujourd'hui payants
     * (ACTIVE ou PAST_DUE — un impayé reste un essai converti).
     */
    public double conversionRate() {
        if (createdInPeriod == 0) {
            return 0d;
        }
        return (double) (activeCount + pastDueCount) / createdInPeriod;
    }

    /**
     * Résiliations de la fenêtre rapportées au parc encore vivant au départ. Le
     * dénominateur inclut les résiliés pour reconstituer la base de début de période.
     */
    public double churnRate() {
        long base = trialCount + activeCount + pastDueCount + cancelledInPeriod;
        if (base == 0) {
            return 0d;
        }
        return (double) cancelledInPeriod / base;
    }

    /** Abonnements donnant accès au service à l'instant de la mesure. */
    public long payingOrTrialing() {
        return trialCount + activeCount + pastDueCount;
    }
}
