package com.yowyob.loyalty.domain.analytics.port.in;

import com.yowyob.loyalty.domain.analytics.model.AnalyticsScope;
import com.yowyob.loyalty.domain.analytics.model.AnalyticsWindow;
import com.yowyob.loyalty.domain.analytics.model.SubscriptionAnalytics;
import reactor.core.publisher.Mono;

/** Flux d'abonnement à l'API : entonnoir, courbe et répartition par plan. */
public interface GetSubscriptionAnalyticsUseCase {

    Mono<SubscriptionAnalytics> subscriptionAnalytics(AnalyticsScope scope, AnalyticsWindow window);
}
