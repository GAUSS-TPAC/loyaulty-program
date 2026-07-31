package com.yowyob.loyalty.domain.analytics.model;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * Réponse complète du flux d'abonnement à l'API : entonnoir, courbe et répartition
 * par plan sur une même fenêtre.
 *
 * <p>Les trois vues sont servies ensemble parce qu'un tableau de bord les affiche
 * ensemble : les séparer en trois appels multiplierait les allers-retours et exposerait
 * l'utilisateur à des chiffres calculés sur des fenêtres légèrement décalées.
 */
public record SubscriptionAnalytics(
        AnalyticsWindow window,
        SubscriptionFunnel funnel,
        List<SubscriptionFlowPoint> flow,
        List<PlanBreakdown> plans,
        Map<String, BigDecimal> monthlyRecurringByCurrency,
        double conversionRate,
        double churnRate
) {}
