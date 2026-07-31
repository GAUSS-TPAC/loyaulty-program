package com.yowyob.loyalty.api.analytics;

import com.yowyob.loyalty.api.analytics.dto.response.BonusAnalyticsResponse;
import com.yowyob.loyalty.api.analytics.dto.response.PaymentAnalyticsResponse;
import com.yowyob.loyalty.api.analytics.dto.response.SubscriptionAnalyticsResponse;
import com.yowyob.loyalty.domain.analytics.model.AnalyticsScope;
import com.yowyob.loyalty.domain.analytics.model.AnalyticsWindow;
import com.yowyob.loyalty.domain.analytics.port.in.GetBonusAnalyticsUseCase;
import com.yowyob.loyalty.domain.analytics.port.in.GetPaymentAnalyticsUseCase;
import com.yowyob.loyalty.domain.analytics.port.in.GetSubscriptionAnalyticsUseCase;
import com.yowyob.loyalty.shared.multitenancy.TenantContextHolder;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.time.Instant;

/**
 * Statistiques de l'organisation appelante.
 *
 * <p>Le périmètre vient exclusivement de {@link TenantContextHolder} : aucun paramètre
 * ne permet de désigner un autre tenant, sans quoi cette route deviendrait une fuite
 * cross-tenant. La vue plateforme a sa propre route, protégée par un secret distinct
 * (voir {@code PlatformAnalyticsController}).
 *
 * <p><strong>Lecture du flux d'abonnement en portée tenant :</strong> un tenant n'a
 * qu'un abonnement ({@code tenant_subscriptions.tenant_id} est UNIQUE). L'entonnoir
 * décrit donc sa propre trajectoire, pas une population ; la valeur analytique côté
 * tenant est surtout dans {@code /payments} et {@code /bonuses}.
 */
@RestController
@RequestMapping("/api/v1/analytics")
@Tag(name = "Analytics", description = "Statistiques, courbes et analyse des bonus du tenant")
public class AnalyticsController {

    private final GetSubscriptionAnalyticsUseCase subscriptionAnalytics;
    private final GetPaymentAnalyticsUseCase paymentAnalytics;
    private final GetBonusAnalyticsUseCase bonusAnalytics;

    public AnalyticsController(GetSubscriptionAnalyticsUseCase subscriptionAnalytics,
                                GetPaymentAnalyticsUseCase paymentAnalytics,
                                GetBonusAnalyticsUseCase bonusAnalytics) {
        this.subscriptionAnalytics = subscriptionAnalytics;
        this.paymentAnalytics = paymentAnalytics;
        this.bonusAnalytics = bonusAnalytics;
    }

    @GetMapping("/subscriptions")
    @Operation(summary = "Flux d'abonnement à l'API : entonnoir, courbe et répartition par plan")
    public Mono<SubscriptionAnalyticsResponse> subscriptions(
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to,
            @RequestParam(required = false) String granularity) {

        AnalyticsWindow window = AnalyticsQuery.window(from, to, granularity);
        return scope()
                .flatMap(scope -> subscriptionAnalytics.subscriptionAnalytics(scope, window))
                .map(SubscriptionAnalyticsResponse::from);
    }

    @GetMapping("/payments")
    @Operation(summary = "Encaissements passerelle et facturation : KPI, courbes et ventilations")
    public Mono<PaymentAnalyticsResponse> payments(
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to,
            @RequestParam(required = false) String granularity) {

        AnalyticsWindow window = AnalyticsQuery.window(from, to, granularity);
        return scope()
                .flatMap(scope -> paymentAnalytics.paymentAnalytics(scope, window))
                .map(PaymentAnalyticsResponse::from);
    }

    @GetMapping("/bonuses")
    @Operation(summary = "Meilleurs bonus : récompenses, règles, canal partenaire et recharges")
    public Mono<BonusAnalyticsResponse> bonuses(
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to,
            @RequestParam(required = false) String granularity,
            @RequestParam(required = false, defaultValue = "10") int limit) {

        AnalyticsWindow window = AnalyticsQuery.window(from, to, granularity);
        return scope()
                .flatMap(scope -> bonusAnalytics.bonusAnalytics(scope, window, limit))
                .map(BonusAnalyticsResponse::from);
    }

    private Mono<AnalyticsScope> scope() {
        return TenantContextHolder.getTenantId().map(AnalyticsScope::ofTenant);
    }
}
