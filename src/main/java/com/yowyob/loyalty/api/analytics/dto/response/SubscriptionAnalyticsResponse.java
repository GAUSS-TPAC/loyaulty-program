package com.yowyob.loyalty.api.analytics.dto.response;

import com.yowyob.loyalty.domain.analytics.model.SubscriptionAnalytics;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Flux d'abonnement à l'API : entonnoir, courbe et répartition par plan.
 *
 * <p>Les taux sont renvoyés en fraction (0.42 = 42 %), pas en pourcentage : la mise en
 * forme — arrondi, signe, locale — appartient au client, qui la fait déjà pour les montants.
 */
public record SubscriptionAnalyticsResponse(
        WindowResponse window,
        Funnel funnel,
        List<FlowPoint> flow,
        List<Plan> plans,
        Map<String, BigDecimal> monthlyRecurringByCurrency,
        double conversionRate,
        double churnRate
) {

    /**
     * @param cancelledInPeriod seul compteur réellement daté ; les autres décrivent l'état
     *                          courant des abonnements créés dans la fenêtre, faute de
     *                          journal des transitions d'abonnement.
     */
    public record Funnel(
            long createdInPeriod,
            long trialCount,
            long activeCount,
            long pastDueCount,
            long cancelledCount,
            long expiredCount,
            long cancelledInPeriod,
            long payingOrTrialing
    ) {}

    public record FlowPoint(
            Instant bucket,
            long started,
            long trialsStarted,
            long cancelled,
            long net
    ) {}

    public record Plan(
            UUID planId,
            String planCode,
            String planName,
            String currency,
            long subscriberCount,
            long activeCount,
            BigDecimal monthlyRecurring
    ) {}

    public static SubscriptionAnalyticsResponse from(SubscriptionAnalytics analytics) {
        var funnel = analytics.funnel();
        return new SubscriptionAnalyticsResponse(
                WindowResponse.from(analytics.window()),
                new Funnel(
                        funnel.createdInPeriod(),
                        funnel.trialCount(),
                        funnel.activeCount(),
                        funnel.pastDueCount(),
                        funnel.cancelledCount(),
                        funnel.expiredCount(),
                        funnel.cancelledInPeriod(),
                        funnel.payingOrTrialing()),
                analytics.flow().stream()
                        .map(point -> new FlowPoint(
                                point.bucket(),
                                point.started(),
                                point.trialsStarted(),
                                point.cancelled(),
                                point.net()))
                        .toList(),
                analytics.plans().stream()
                        .map(plan -> new Plan(
                                plan.planId(),
                                plan.planCode(),
                                plan.planName(),
                                plan.currency(),
                                plan.subscriberCount(),
                                plan.activeCount(),
                                plan.monthlyRecurring()))
                        .toList(),
                analytics.monthlyRecurringByCurrency(),
                analytics.conversionRate(),
                analytics.churnRate()
        );
    }
}
