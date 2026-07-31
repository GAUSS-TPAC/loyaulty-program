package com.yowyob.loyalty.domain.analytics.model;

import com.yowyob.loyalty.domain.shared.exception.DomainValidationException;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * Fenêtre d'observation d'une agrégation, bornes {@code [from, to[}.
 *
 * <p>Borne haute exclue : deux fenêtres consécutives ne comptent jamais deux fois
 * l'événement pile à la frontière, et la somme des tranches égale toujours le total.
 *
 * <p>Le plafond de {@link #MAX_BUCKETS} points n'est pas cosmétique : les requêtes
 * d'agrégation balayent l'intervalle complet, et une courbe journalière sur dix ans
 * coûterait autant qu'un scan de table pour un graphique illisible.
 */
public record AnalyticsWindow(Instant from, Instant to, TimeGranularity granularity) {

    /** Au-delà, la courbe n'est plus lisible et la requête devient un scan complet. */
    public static final int MAX_BUCKETS = 400;

    private static final int DEFAULT_DAYS = 30;

    public AnalyticsWindow {
        if (from == null || to == null) {
            throw new DomainValidationException("Fenêtre d'analyse incomplète : from et to sont requis");
        }
        if (!from.isBefore(to)) {
            throw new DomainValidationException(
                    "Fenêtre d'analyse invalide : from (" + from + ") doit précéder to (" + to + ")");
        }
        if (granularity == null) {
            granularity = TimeGranularity.DAY;
        }
        long buckets = estimateBuckets(from, to, granularity);
        if (buckets > MAX_BUCKETS) {
            throw new DomainValidationException(
                    "Fenêtre trop large pour la granularité " + granularity + " : "
                            + buckets + " points demandés, maximum " + MAX_BUCKETS
                            + ". Élargir le pas ou raccourcir la période.");
        }
    }

    /** Fenêtre par défaut des tableaux de bord : 30 derniers jours, pas journalier. */
    public static AnalyticsWindow lastThirtyDays(Instant now) {
        return new AnalyticsWindow(now.minus(DEFAULT_DAYS, ChronoUnit.DAYS), now, TimeGranularity.DAY);
    }

    /**
     * Complète les bornes absentes plutôt que d'échouer : les tableaux de bord appellent
     * souvent sans paramètre, et une console qui ne fournit que {@code from} attend
     * implicitement « jusqu'à maintenant ».
     */
    public static AnalyticsWindow of(Instant from, Instant to, TimeGranularity granularity, Instant now) {
        Instant end = to != null ? to : now;
        Instant start = from != null ? from : end.minus(DEFAULT_DAYS, ChronoUnit.DAYS);
        return new AnalyticsWindow(start, end, granularity);
    }

    /**
     * Fenêtre de même durée immédiatement antérieure, pour exprimer une évolution
     * (« +12 % vs période précédente ») sans que l'appelant ait à la recalculer.
     */
    public AnalyticsWindow previousPeriod() {
        return new AnalyticsWindow(from.minus(durationMillis(), ChronoUnit.MILLIS), from, granularity);
    }

    public long durationMillis() {
        return to.toEpochMilli() - from.toEpochMilli();
    }

    private static long estimateBuckets(Instant from, Instant to, TimeGranularity granularity) {
        long step = granularity.approximateStep().toMillis();
        return (to.toEpochMilli() - from.toEpochMilli()) / step + 1;
    }
}
