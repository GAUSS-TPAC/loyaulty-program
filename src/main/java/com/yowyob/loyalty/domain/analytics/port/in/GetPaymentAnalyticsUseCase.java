package com.yowyob.loyalty.domain.analytics.port.in;

import com.yowyob.loyalty.domain.analytics.model.AnalyticsScope;
import com.yowyob.loyalty.domain.analytics.model.AnalyticsWindow;
import com.yowyob.loyalty.domain.analytics.model.PaymentAnalytics;
import reactor.core.publisher.Mono;

/** Statistiques et courbes de paiement : encaissement passerelle et facturation SaaS. */
public interface GetPaymentAnalyticsUseCase {

    Mono<PaymentAnalytics> paymentAnalytics(AnalyticsScope scope, AnalyticsWindow window);
}
