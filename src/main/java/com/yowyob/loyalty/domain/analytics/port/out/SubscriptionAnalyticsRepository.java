package com.yowyob.loyalty.domain.analytics.port.out;

import com.yowyob.loyalty.domain.analytics.model.AnalyticsScope;
import com.yowyob.loyalty.domain.analytics.model.AnalyticsWindow;
import com.yowyob.loyalty.domain.analytics.model.PlanBreakdown;
import com.yowyob.loyalty.domain.analytics.model.SubscriptionFlowPoint;
import com.yowyob.loyalty.domain.analytics.model.SubscriptionFunnel;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Agrégations du flux d'abonnement à l'API.
 *
 * <p>Aucune méthode ne renvoie de taux : la persistance compte, le domaine interprète.
 * Un taux calculé en SQL serait invisible aux tests unitaires et impossible à corriger
 * sans migration.
 */
public interface SubscriptionAnalyticsRepository {

    Mono<SubscriptionFunnel> funnel(AnalyticsScope scope, AnalyticsWindow window);

    /** Courbe des créations, essais et résiliations, une ligne par tranche non vide. */
    Flux<SubscriptionFlowPoint> flow(AnalyticsScope scope, AnalyticsWindow window);

    /**
     * Répartition du parc par plan. Instantané à la date de la requête, non borné par
     * la fenêtre : « combien d'abonnés sur le plan PRO aujourd'hui » n'est pas une
     * question de période.
     */
    Flux<PlanBreakdown> planBreakdown(AnalyticsScope scope);
}
