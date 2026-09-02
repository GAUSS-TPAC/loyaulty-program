package com.yowyob.loyalty.domain.analytics.service;

import com.yowyob.loyalty.domain.analytics.model.AnalyticsScope;
import com.yowyob.loyalty.domain.analytics.model.AnalyticsWindow;
import com.yowyob.loyalty.domain.analytics.model.BonusAnalytics;
import com.yowyob.loyalty.domain.analytics.model.BonusPerformance;
import com.yowyob.loyalty.domain.analytics.model.PaymentAnalytics;
import com.yowyob.loyalty.domain.analytics.model.PlanBreakdown;
import com.yowyob.loyalty.domain.analytics.model.RawBonusRow;
import com.yowyob.loyalty.domain.analytics.model.SubscriptionAnalytics;
import com.yowyob.loyalty.domain.analytics.model.SubscriptionFunnel;
import com.yowyob.loyalty.domain.analytics.port.in.GetBonusAnalyticsUseCase;
import com.yowyob.loyalty.domain.analytics.port.in.GetPaymentAnalyticsUseCase;
import com.yowyob.loyalty.domain.analytics.port.in.GetSubscriptionAnalyticsUseCase;
import com.yowyob.loyalty.domain.analytics.port.out.BonusAnalyticsRepository;
import com.yowyob.loyalty.domain.analytics.port.out.PaymentAnalyticsRepository;
import com.yowyob.loyalty.domain.analytics.port.out.SubscriptionAnalyticsRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Assemble les vues analytiques à partir des agrégations brutes.
 *
 * <p>Toute la lecture des chiffres vit ici : taux de conversion, churn, MRR par devise,
 * classement des bonus. La persistance ne fait que compter et sommer, ce qui rend ces
 * règles testables sans base et modifiables sans migration.
 *
 * <p>Le même service sert la console tenant et la console plateforme : seul le
 * {@link AnalyticsScope} change, et il est fourni par le contrôleur — jamais deviné ici.
 */
public class AnalyticsDomainService implements
        GetSubscriptionAnalyticsUseCase, GetPaymentAnalyticsUseCase, GetBonusAnalyticsUseCase {

    /** Garde-fou : un palmarès plus long qu'un écran n'a pas d'usage et coûte une requête large. */
    private static final int MAX_LIMIT = 50;
    private static final int DEFAULT_LIMIT = 10;

    private final SubscriptionAnalyticsRepository subscriptionRepository;
    private final PaymentAnalyticsRepository paymentRepository;
    private final BonusAnalyticsRepository bonusRepository;

    public AnalyticsDomainService(SubscriptionAnalyticsRepository subscriptionRepository,
                                   PaymentAnalyticsRepository paymentRepository,
                                   BonusAnalyticsRepository bonusRepository) {
        this.subscriptionRepository = subscriptionRepository;
        this.paymentRepository = paymentRepository;
        this.bonusRepository = bonusRepository;
    }

    @Override
    public Mono<SubscriptionAnalytics> subscriptionAnalytics(AnalyticsScope scope, AnalyticsWindow window) {
        return Mono.zip(
                subscriptionRepository.funnel(scope, window).defaultIfEmpty(SubscriptionFunnel.empty()),
                subscriptionRepository.flow(scope, window).collectList(),
                subscriptionRepository.planBreakdown(scope).collectList()
        ).map(tuple -> {
            SubscriptionFunnel funnel = tuple.getT1();
            List<PlanBreakdown> plans = tuple.getT3();
            return new SubscriptionAnalytics(
                    window,
                    funnel,
                    tuple.getT2(),
                    plans,
                    monthlyRecurringByCurrency(plans),
                    funnel.conversionRate(),
                    funnel.churnRate()
            );
        });
    }

    @Override
    public Mono<PaymentAnalytics> paymentAnalytics(AnalyticsScope scope, AnalyticsWindow window) {
        return Mono.zip(
                paymentRepository.gatewayStats(scope, window).collectList(),
                paymentRepository.gatewaySeries(scope, window).collectList(),
                paymentRepository.gatewayBreakdown(scope, window).collectList(),
                paymentRepository.billingStats(scope, window).collectList(),
                paymentRepository.billingSeries(scope, window).collectList()
        ).map(tuple -> new PaymentAnalytics(
                window,
                tuple.getT1(),
                tuple.getT2(),
                tuple.getT3(),
                tuple.getT4(),
                tuple.getT5()
        ));
    }

    @Override
    public Mono<BonusAnalytics> bonusAnalytics(AnalyticsScope scope, AnalyticsWindow window, int limit) {
        int effectiveLimit = normalizeLimit(limit);
        return Mono.zip(
                rank(bonusRepository.topRewards(scope, window, effectiveLimit)),
                rank(bonusRepository.topRules(scope, window, effectiveLimit)),
                // La ventilation bonification n'a que deux lignes possibles (crédit/débit) :
                // la borner n'apporterait rien.
                rank(bonusRepository.bonificationBreakdown(scope, window)),
                rank(bonusRepository.topUpBreakdown(scope, window, effectiveLimit))
        ).map(tuple -> new BonusAnalytics(
                window,
                tuple.getT1(),
                tuple.getT2(),
                tuple.getT3(),
                tuple.getT4()
        ));
    }

    /**
     * Le classement est calculé par famille sur la liste complète renvoyée par la
     * requête : normaliser sur un sous-ensemble déjà tronqué fausserait le score du
     * premier élément (il vaudrait toujours 100 quel que soit son volume réel).
     */
    private Mono<List<BonusPerformance>> rank(Flux<RawBonusRow> rows) {
        return rows.collectList().map(BonusPerformance::rank);
    }

    /**
     * MRR par devise. Le regroupement est indispensable : additionner des plans en XAF
     * et en EUR donnerait un « revenu récurrent » qui ne correspond à aucune somme réelle.
     */
    private Map<String, BigDecimal> monthlyRecurringByCurrency(List<PlanBreakdown> plans) {
        Map<String, BigDecimal> totals = new LinkedHashMap<>();
        for (PlanBreakdown plan : plans) {
            totals.merge(plan.currency(), plan.monthlyRecurring(), BigDecimal::add);
        }
        return totals;
    }

    private int normalizeLimit(int limit) {
        if (limit <= 0) {
            return DEFAULT_LIMIT;
        }
        return Math.min(limit, MAX_LIMIT);
    }
}
