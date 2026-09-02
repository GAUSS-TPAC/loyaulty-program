package com.yowyob.loyalty.domain.analytics.port.in;

import com.yowyob.loyalty.domain.analytics.model.AnalyticsScope;
import com.yowyob.loyalty.domain.analytics.model.AnalyticsWindow;
import com.yowyob.loyalty.domain.analytics.model.BonusAnalytics;
import reactor.core.publisher.Mono;

/** Analyse des meilleurs bonus, toutes sources confondues. */
public interface GetBonusAnalyticsUseCase {

    /** @param limit taille de chaque palmarès, borné par l'implémentation */
    Mono<BonusAnalytics> bonusAnalytics(AnalyticsScope scope, AnalyticsWindow window, int limit);
}
