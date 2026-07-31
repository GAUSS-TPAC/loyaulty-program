package com.yowyob.loyalty.domain.analytics.port.out;

import com.yowyob.loyalty.domain.analytics.model.AnalyticsScope;
import com.yowyob.loyalty.domain.analytics.model.AnalyticsWindow;
import com.yowyob.loyalty.domain.analytics.model.BillingSeriesPoint;
import com.yowyob.loyalty.domain.analytics.model.BillingStats;
import com.yowyob.loyalty.domain.analytics.model.GatewayStats;
import com.yowyob.loyalty.domain.analytics.model.PaymentBreakdown;
import com.yowyob.loyalty.domain.analytics.model.PaymentSeriesPoint;
import reactor.core.publisher.Flux;

/**
 * Agrégations des deux flux monétaires : encaissement wallet via la passerelle, et
 * facturation des abonnements. Les deux ne se somment jamais (voir {@code PaymentAnalytics}).
 */
public interface PaymentAnalyticsRepository {

    /** Une ligne par devise présente dans la fenêtre. */
    Flux<GatewayStats> gatewayStats(AnalyticsScope scope, AnalyticsWindow window);

    Flux<PaymentSeriesPoint> gatewaySeries(AnalyticsScope scope, AnalyticsWindow window);

    /** Ventilation par moyen de paiement puis par PSP, dans une seule liste étiquetée. */
    Flux<PaymentBreakdown> gatewayBreakdown(AnalyticsScope scope, AnalyticsWindow window);

    Flux<BillingStats> billingStats(AnalyticsScope scope, AnalyticsWindow window);

    Flux<BillingSeriesPoint> billingSeries(AnalyticsScope scope, AnalyticsWindow window);
}
