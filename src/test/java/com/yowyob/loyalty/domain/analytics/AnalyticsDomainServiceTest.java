package com.yowyob.loyalty.domain.analytics;

import com.yowyob.loyalty.domain.analytics.model.AnalyticsScope;
import com.yowyob.loyalty.domain.analytics.model.AnalyticsWindow;
import com.yowyob.loyalty.domain.analytics.model.BillingSeriesPoint;
import com.yowyob.loyalty.domain.analytics.model.BillingStats;
import com.yowyob.loyalty.domain.analytics.model.BonusAnalytics;
import com.yowyob.loyalty.domain.analytics.model.BonusKind;
import com.yowyob.loyalty.domain.analytics.model.GatewayStats;
import com.yowyob.loyalty.domain.analytics.model.PaymentAnalytics;
import com.yowyob.loyalty.domain.analytics.model.PaymentBreakdown;
import com.yowyob.loyalty.domain.analytics.model.PaymentSeriesPoint;
import com.yowyob.loyalty.domain.analytics.model.PlanBreakdown;
import com.yowyob.loyalty.domain.analytics.model.RawBonusRow;
import com.yowyob.loyalty.domain.analytics.model.SubscriptionAnalytics;
import com.yowyob.loyalty.domain.analytics.model.SubscriptionFlowPoint;
import com.yowyob.loyalty.domain.analytics.model.SubscriptionFunnel;
import com.yowyob.loyalty.domain.analytics.model.TimeGranularity;
import com.yowyob.loyalty.domain.analytics.port.out.BonusAnalyticsRepository;
import com.yowyob.loyalty.domain.analytics.port.out.PaymentAnalyticsRepository;
import com.yowyob.loyalty.domain.analytics.port.out.SubscriptionAnalyticsRepository;
import com.yowyob.loyalty.domain.analytics.service.AnalyticsDomainService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AnalyticsDomainServiceTest {

    private static final Instant NOW = Instant.parse("2026-07-31T00:00:00Z");
    private static final AnalyticsWindow WINDOW =
            new AnalyticsWindow(NOW.minus(30, ChronoUnit.DAYS), NOW, TimeGranularity.DAY);

    private FakeSubscriptionRepository subscriptions;
    private FakePaymentRepository payments;
    private FakeBonusRepository bonuses;
    private AnalyticsDomainService service;

    @BeforeEach
    void setUp() {
        subscriptions = new FakeSubscriptionRepository();
        payments = new FakePaymentRepository();
        bonuses = new FakeBonusRepository();
        service = new AnalyticsDomainService(subscriptions, payments, bonuses);
    }

    // ── Abonnements ───────────────────────────────────────────────────────────

    @Test
    void subscriptionAnalytics_groupsMonthlyRecurringByCurrency() {
        // Deux plans en XAF, un en EUR : le MRR ne doit jamais être un scalaire unique.
        subscriptions.plans = List.of(
                plan("PRO", "XAF", new BigDecimal("9900")),
                plan("ENTERPRISE", "XAF", new BigDecimal("49900")),
                plan("PRO_EU", "EUR", new BigDecimal("15")));

        SubscriptionAnalytics result =
                service.subscriptionAnalytics(AnalyticsScope.platformWide(), WINDOW).block();

        assertEquals(new BigDecimal("59800"), result.monthlyRecurringByCurrency().get("XAF"));
        assertEquals(new BigDecimal("15"), result.monthlyRecurringByCurrency().get("EUR"));
    }

    @Test
    void subscriptionAnalytics_exposesRatesComputedFromFunnel() {
        subscriptions.funnel = new SubscriptionFunnel(10, 3, 4, 2, 1, 0, 1);

        SubscriptionAnalytics result =
                service.subscriptionAnalytics(AnalyticsScope.platformWide(), WINDOW).block();

        assertEquals(result.funnel().conversionRate(), result.conversionRate(), 1e-9);
        assertEquals(result.funnel().churnRate(), result.churnRate(), 1e-9);
    }

    @Test
    void subscriptionAnalytics_survivesEmptyFunnel() {
        // Une base vide ne doit pas produire un Mono vide : le tableau de bord doit
        // pouvoir afficher des zéros plutôt qu'une erreur.
        subscriptions.funnel = null;

        SubscriptionAnalytics result =
                service.subscriptionAnalytics(AnalyticsScope.platformWide(), WINDOW).block();

        assertEquals(0, result.funnel().createdInPeriod());
        assertTrue(result.monthlyRecurringByCurrency().isEmpty());
    }

    @Test
    void subscriptionAnalytics_passesScopeThroughToRepository() {
        AnalyticsScope scope = AnalyticsScope.ofTenant(
                new com.yowyob.loyalty.domain.shared.model.TenantId(UUID.randomUUID()));

        service.subscriptionAnalytics(scope, WINDOW).block();

        // Le périmètre décidé par le contrôleur doit atteindre chaque requête sans
        // réinterprétation : c'est lui qui porte l'isolation entre tenants.
        assertTrue(subscriptions.seenScopes.stream().allMatch(s -> s.equals(scope)));
        assertEquals(3, subscriptions.seenScopes.size());
    }

    // ── Paiements ─────────────────────────────────────────────────────────────

    @Test
    void paymentAnalytics_keepsGatewayAndBillingSeparate() {
        payments.gateway = List.of(new GatewayStats(
                "XAF", 10, 8, 2, 0, new BigDecimal("8000"), new BigDecimal("10000")));
        payments.billing = List.of(new BillingStats(
                "XAF", 3, 2, 1, 0, new BigDecimal("29700"), new BigDecimal("19800")));

        PaymentAnalytics result =
                service.paymentAnalytics(AnalyticsScope.platformWide(), WINDOW).block();

        // Recharges de membres et revenu SaaS restent dans deux blocs distincts.
        assertEquals(new BigDecimal("8000"), result.gateway().get(0).collectedAmount());
        assertEquals(new BigDecimal("19800"), result.billing().get(0).collectedAmount());
    }

    // ── Bonus ─────────────────────────────────────────────────────────────────

    @Test
    void bonusAnalytics_ranksEachFamilySeparately() {
        bonuses.rewards = List.of(
                row(BonusKind.REWARD, "r-small", 10, 10),
                row(BonusKind.REWARD, "r-big", 100, 50));
        bonuses.topUps = List.of(
                row(BonusKind.WALLET_TOPUP, "MTN", 200, 180),
                row(BonusKind.WALLET_TOPUP, "STRIPE", 20, 19));

        BonusAnalytics result =
                service.bonusAnalytics(AnalyticsScope.platformWide(), WINDOW, 10).block();

        assertEquals("r-big", result.rewards().get(0).key());
        assertEquals("MTN", result.topUps().get(0).key());
        // Chaque famille est normalisée sur son propre maximum : les deux têtes de
        // classement saturent la composante volume (60) malgré des volumes absolus très
        // différents (100 vs 200), et ne se départagent que sur leur conversion.
        assertEquals(60d + 40d * 0.5d, result.rewards().get(0).score(), 1e-9);
        assertEquals(60d + 40d * 0.9d, result.topUps().get(0).score(), 1e-9);
    }

    @Test
    void bonusAnalytics_highlightsSkipEmptyFamilies() {
        bonuses.rewards = List.of(row(BonusKind.REWARD, "r1", 5, 5));
        bonuses.rules = List.of();
        bonuses.bonification = List.of();
        bonuses.topUps = List.of(row(BonusKind.WALLET_TOPUP, "MTN", 5, 5));

        BonusAnalytics result =
                service.bonusAnalytics(AnalyticsScope.platformWide(), WINDOW, 10).block();

        assertEquals(2, result.highlights().size());
    }

    @Test
    void bonusAnalytics_clampsLimitAndFallsBackOnDefault() {
        service.bonusAnalytics(AnalyticsScope.platformWide(), WINDOW, 0).block();
        assertEquals(10, bonuses.lastLimit);

        service.bonusAnalytics(AnalyticsScope.platformWide(), WINDOW, 9999).block();
        assertEquals(50, bonuses.lastLimit);

        service.bonusAnalytics(AnalyticsScope.platformWide(), WINDOW, 25).block();
        assertEquals(25, bonuses.lastLimit);
    }

    // ── Fixtures ──────────────────────────────────────────────────────────────

    private static PlanBreakdown plan(String code, String currency, BigDecimal mrr) {
        return new PlanBreakdown(UUID.randomUUID(), code, code, currency, 1, 1, mrr);
    }

    private static RawBonusRow row(BonusKind kind, String key, long volume, long converted) {
        return new RawBonusRow(kind, key, key, volume, converted, BigDecimal.valueOf(volume), null);
    }

    private static final class FakeSubscriptionRepository implements SubscriptionAnalyticsRepository {
        SubscriptionFunnel funnel = SubscriptionFunnel.empty();
        List<SubscriptionFlowPoint> flow = List.of();
        List<PlanBreakdown> plans = List.of();
        final List<AnalyticsScope> seenScopes = new ArrayList<>();

        @Override
        public Mono<SubscriptionFunnel> funnel(AnalyticsScope scope, AnalyticsWindow window) {
            seenScopes.add(scope);
            return funnel == null ? Mono.empty() : Mono.just(funnel);
        }

        @Override
        public Flux<SubscriptionFlowPoint> flow(AnalyticsScope scope, AnalyticsWindow window) {
            seenScopes.add(scope);
            return Flux.fromIterable(flow);
        }

        @Override
        public Flux<PlanBreakdown> planBreakdown(AnalyticsScope scope) {
            seenScopes.add(scope);
            return Flux.fromIterable(plans);
        }
    }

    private static final class FakePaymentRepository implements PaymentAnalyticsRepository {
        List<GatewayStats> gateway = List.of();
        List<BillingStats> billing = List.of();

        @Override
        public Flux<GatewayStats> gatewayStats(AnalyticsScope scope, AnalyticsWindow window) {
            return Flux.fromIterable(gateway);
        }

        @Override
        public Flux<PaymentSeriesPoint> gatewaySeries(AnalyticsScope scope, AnalyticsWindow window) {
            return Flux.empty();
        }

        @Override
        public Flux<PaymentBreakdown> gatewayBreakdown(AnalyticsScope scope, AnalyticsWindow window) {
            return Flux.empty();
        }

        @Override
        public Flux<BillingStats> billingStats(AnalyticsScope scope, AnalyticsWindow window) {
            return Flux.fromIterable(billing);
        }

        @Override
        public Flux<BillingSeriesPoint> billingSeries(AnalyticsScope scope, AnalyticsWindow window) {
            return Flux.empty();
        }
    }

    private static final class FakeBonusRepository implements BonusAnalyticsRepository {
        List<RawBonusRow> rewards = List.of();
        List<RawBonusRow> rules = List.of();
        List<RawBonusRow> bonification = List.of();
        List<RawBonusRow> topUps = List.of();
        int lastLimit = -1;

        @Override
        public Flux<RawBonusRow> topRewards(AnalyticsScope scope, AnalyticsWindow window, int limit) {
            lastLimit = limit;
            return Flux.fromIterable(rewards);
        }

        @Override
        public Flux<RawBonusRow> topRules(AnalyticsScope scope, AnalyticsWindow window, int limit) {
            lastLimit = limit;
            return Flux.fromIterable(rules);
        }

        @Override
        public Flux<RawBonusRow> bonificationBreakdown(AnalyticsScope scope, AnalyticsWindow window) {
            return Flux.fromIterable(bonification);
        }

        @Override
        public Flux<RawBonusRow> topUpBreakdown(AnalyticsScope scope, AnalyticsWindow window, int limit) {
            lastLimit = limit;
            return Flux.fromIterable(topUps);
        }
    }
}
