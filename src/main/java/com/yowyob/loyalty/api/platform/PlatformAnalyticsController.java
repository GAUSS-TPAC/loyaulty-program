package com.yowyob.loyalty.api.platform;

import com.yowyob.loyalty.api.analytics.AnalyticsQuery;
import com.yowyob.loyalty.api.analytics.dto.response.BonusAnalyticsResponse;
import com.yowyob.loyalty.api.analytics.dto.response.PaymentAnalyticsResponse;
import com.yowyob.loyalty.api.analytics.dto.response.SubscriptionAnalyticsResponse;
import com.yowyob.loyalty.domain.analytics.model.AnalyticsScope;
import com.yowyob.loyalty.domain.analytics.model.AnalyticsWindow;
import com.yowyob.loyalty.domain.analytics.port.in.GetBonusAnalyticsUseCase;
import com.yowyob.loyalty.domain.analytics.port.in.GetPaymentAnalyticsUseCase;
import com.yowyob.loyalty.domain.analytics.port.in.GetSubscriptionAnalyticsUseCase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.time.Instant;

/**
 * Mêmes statistiques que la console tenant, mais agrégées sur toute la plateforme.
 *
 * <p>Le préfixe {@code /api/v1/admin/platform} n'est pas décoratif : c'est lui qui place
 * ces routes sous {@link com.yowyob.loyalty.shared.security.PlatformAdminAuthFilter}
 * (secret {@code X-Platform-Admin-Key}), le seul garde-fou de ces vues cross-tenant —
 * ni JWT de tenant ni clé API ne s'appliquent ici. Déplacer ce contrôleur hors du
 * préfixe le rendrait public.
 *
 * <p>Le périmètre est câblé en dur sur {@link AnalyticsScope#platformWide()} : aucun
 * paramètre ne le pilote, il n'y a donc rien à valider côté requête.
 */
@RestController
@RequestMapping("/api/v1/admin/platform/analytics")
@Tag(name = "Platform Admin", description = "Console plateforme (cross-tenant)")
public class PlatformAnalyticsController {

    private final GetSubscriptionAnalyticsUseCase subscriptionAnalytics;
    private final GetPaymentAnalyticsUseCase paymentAnalytics;
    private final GetBonusAnalyticsUseCase bonusAnalytics;

    public PlatformAnalyticsController(GetSubscriptionAnalyticsUseCase subscriptionAnalytics,
                                        GetPaymentAnalyticsUseCase paymentAnalytics,
                                        GetBonusAnalyticsUseCase bonusAnalytics) {
        this.subscriptionAnalytics = subscriptionAnalytics;
        this.paymentAnalytics = paymentAnalytics;
        this.bonusAnalytics = bonusAnalytics;
    }

    @GetMapping("/subscriptions")
    @Operation(summary = "Flux d'abonnement à l'API, toutes organisations confondues")
    public Mono<SubscriptionAnalyticsResponse> subscriptions(
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to,
            @RequestParam(required = false) String granularity) {

        AnalyticsWindow window = AnalyticsQuery.window(from, to, granularity);
        return subscriptionAnalytics.subscriptionAnalytics(AnalyticsScope.platformWide(), window)
                .map(SubscriptionAnalyticsResponse::from);
    }

    @GetMapping("/payments")
    @Operation(summary = "Encaissements et revenu SaaS de la plateforme")
    public Mono<PaymentAnalyticsResponse> payments(
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to,
            @RequestParam(required = false) String granularity) {

        AnalyticsWindow window = AnalyticsQuery.window(from, to, granularity);
        return paymentAnalytics.paymentAnalytics(AnalyticsScope.platformWide(), window)
                .map(PaymentAnalyticsResponse::from);
    }

    @GetMapping("/bonuses")
    @Operation(summary = "Meilleurs bonus toutes organisations confondues")
    public Mono<BonusAnalyticsResponse> bonuses(
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to,
            @RequestParam(required = false) String granularity,
            @RequestParam(required = false, defaultValue = "10") int limit) {

        AnalyticsWindow window = AnalyticsQuery.window(from, to, granularity);
        return bonusAnalytics.bonusAnalytics(AnalyticsScope.platformWide(), window, limit)
                .map(BonusAnalyticsResponse::from);
    }
}
