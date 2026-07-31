package com.yowyob.loyalty.domain.analytics.model;

import java.time.Instant;

/**
 * Un point de la courbe du flux d'abonnement.
 *
 * <p>{@code bucket} est le début de la tranche, pas son milieu : deux séries de
 * granularités différentes restent alignables sur le même axe.
 */
public record SubscriptionFlowPoint(
        Instant bucket,
        long started,
        long trialsStarted,
        long cancelled
) {

    /** Solde net d'abonnements sur la tranche : c'est la courbe que lit un dirigeant. */
    public long net() {
        return started - cancelled;
    }
}
