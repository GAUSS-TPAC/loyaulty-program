package com.yowyob.loyalty.domain.analytics;

import com.yowyob.loyalty.domain.analytics.model.AnalyticsScope;
import com.yowyob.loyalty.domain.analytics.model.AnalyticsWindow;
import com.yowyob.loyalty.domain.analytics.model.BillingStats;
import com.yowyob.loyalty.domain.analytics.model.BonusKind;
import com.yowyob.loyalty.domain.analytics.model.BonusPerformance;
import com.yowyob.loyalty.domain.analytics.model.GatewayStats;
import com.yowyob.loyalty.domain.analytics.model.RawBonusRow;
import com.yowyob.loyalty.domain.analytics.model.SubscriptionFunnel;
import com.yowyob.loyalty.domain.analytics.model.TimeGranularity;
import com.yowyob.loyalty.domain.shared.exception.DomainValidationException;
import com.yowyob.loyalty.domain.shared.model.TenantId;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AnalyticsModelsTest {

    private static final Instant NOW = Instant.parse("2026-07-31T00:00:00Z");

    // ── Fenêtre ───────────────────────────────────────────────────────────────

    @Test
    void window_rejectsInvertedBounds() {
        assertThrows(DomainValidationException.class,
                () -> new AnalyticsWindow(NOW, NOW.minus(1, ChronoUnit.DAYS), TimeGranularity.DAY));
    }

    @Test
    void window_rejectsEmptyRange() {
        // Bornes égales : intervalle [from, to[ vide, aucune donnée ne peut y tomber.
        assertThrows(DomainValidationException.class,
                () -> new AnalyticsWindow(NOW, NOW, TimeGranularity.DAY));
    }

    @Test
    void window_rejectsTooManyBuckets() {
        // 10 ans au pas journalier : ~3650 points, bien au-delà du plafond.
        assertThrows(DomainValidationException.class,
                () -> new AnalyticsWindow(NOW.minus(3650, ChronoUnit.DAYS), NOW, TimeGranularity.DAY));
    }

    @Test
    void window_acceptsLongRangeOnCoarserGranularity() {
        // La même période repasse sous le plafond au pas mensuel : c'est la granularité
        // qui est en cause, pas la durée.
        AnalyticsWindow window =
                new AnalyticsWindow(NOW.minus(3650, ChronoUnit.DAYS), NOW, TimeGranularity.MONTH);
        assertEquals(TimeGranularity.MONTH, window.granularity());
    }

    @Test
    void window_fillsMissingBoundsFromNow() {
        AnalyticsWindow window = AnalyticsWindow.of(null, null, TimeGranularity.DAY, NOW);

        assertEquals(NOW, window.to());
        assertEquals(NOW.minus(30, ChronoUnit.DAYS), window.from());
    }

    @Test
    void window_fillsOnlyMissingBound() {
        Instant from = NOW.minus(3, ChronoUnit.DAYS);
        AnalyticsWindow window = AnalyticsWindow.of(from, null, TimeGranularity.DAY, NOW);

        assertEquals(from, window.from());
        assertEquals(NOW, window.to());
    }

    @Test
    void window_previousPeriodHasSameDurationAndEndsWhereCurrentStarts() {
        AnalyticsWindow current =
                new AnalyticsWindow(NOW.minus(7, ChronoUnit.DAYS), NOW, TimeGranularity.DAY);
        AnalyticsWindow previous = current.previousPeriod();

        assertEquals(current.from(), previous.to());
        assertEquals(current.durationMillis(), previous.durationMillis());
    }

    @Test
    void granularity_defaultsToDayAndRejectsUnknown() {
        assertEquals(TimeGranularity.DAY, TimeGranularity.parse(null));
        assertEquals(TimeGranularity.MONTH, TimeGranularity.parse("month"));
        assertThrows(DomainValidationException.class, () -> TimeGranularity.parse("HOURLY"));
    }

    // ── Périmètre ─────────────────────────────────────────────────────────────

    @Test
    void scope_distinguishesPlatformFromTenant() {
        assertTrue(AnalyticsScope.platformWide().isPlatformWide());

        TenantId tenantId = new TenantId(UUID.randomUUID());
        AnalyticsScope scope = AnalyticsScope.ofTenant(tenantId);
        assertFalse(scope.isPlatformWide());
        assertEquals(tenantId, scope.tenant().orElseThrow());
    }

    @Test
    void scope_refusesNullTenantRatherThanSilentlyWideningToPlatform() {
        // Le piège à éviter : un tenantId absent qui deviendrait une vue cross-tenant.
        assertThrows(IllegalArgumentException.class, () -> AnalyticsScope.ofTenant(null));
    }

    // ── Entonnoir d'abonnement ────────────────────────────────────────────────

    @Test
    void funnel_conversionCountsPastDueAsConverted() {
        // 10 créés, 4 ACTIVE + 2 PAST_DUE : un impayé reste un essai converti.
        SubscriptionFunnel funnel = new SubscriptionFunnel(10, 3, 4, 2, 1, 0, 1);

        assertEquals(0.6d, funnel.conversionRate(), 1e-9);
        assertEquals(9, funnel.payingOrTrialing());
    }

    @Test
    void funnel_ratesAreZeroWhenNothingHappened() {
        SubscriptionFunnel empty = SubscriptionFunnel.empty();

        assertEquals(0d, empty.conversionRate(), 1e-9);
        assertEquals(0d, empty.churnRate(), 1e-9);
    }

    @Test
    void funnel_churnUsesStartOfPeriodBase() {
        // Base reconstituée = 3 TRIAL + 4 ACTIVE + 1 PAST_DUE + 2 résiliés = 10.
        SubscriptionFunnel funnel = new SubscriptionFunnel(10, 3, 4, 1, 2, 0, 2);

        assertEquals(0.2d, funnel.churnRate(), 1e-9);
    }

    // ── Passerelle ────────────────────────────────────────────────────────────

    @Test
    void gateway_successRateIgnoresPendingAttempts() {
        // 8 abouties, 2 échouées, 90 encore en attente : le taux porte sur les 10 tranchées.
        GatewayStats stats = new GatewayStats(
                "XAF", 100, 8, 2, 90, new BigDecimal("8000"), new BigDecimal("100000"));

        assertEquals(0.8d, stats.successRate(), 1e-9);
    }

    @Test
    void gateway_averageTicketUsesSuccessfulPaymentsOnly() {
        GatewayStats stats = new GatewayStats(
                "XAF", 10, 4, 6, 0, new BigDecimal("10000"), new BigDecimal("25000"));

        assertEquals(new BigDecimal("2500.00"), stats.averageTicket());
    }

    @Test
    void gateway_emptyStatsDoNotDivideByZero() {
        GatewayStats empty = GatewayStats.empty("XAF");

        assertEquals(0d, empty.successRate(), 1e-9);
        assertEquals(BigDecimal.ZERO, empty.averageTicket());
    }

    // ── Facturation ───────────────────────────────────────────────────────────

    @Test
    void billing_outstandingNeverGoesNegative() {
        // Recouvrement supérieur au facturé de la fenêtre : des factures antérieures ont
        // été payées pendant la période. Le reste à recouvrer est plancher à zéro.
        BillingStats stats = new BillingStats(
                "XAF", 2, 5, 0, 0, new BigDecimal("1000"), new BigDecimal("4000"));

        assertEquals(BigDecimal.ZERO, stats.outstandingAmount());
    }

    @Test
    void billing_collectionRateIsZeroWhenNothingBilled() {
        assertEquals(0d, BillingStats.empty("XAF").collectionRate(), 1e-9);
    }

    // ── Classement des bonus ──────────────────────────────────────────────────

    @Test
    void rank_normalisesVolumeAgainstBestOfSameFamily() {
        // Le meilleur volume de la famille obtient 60 points de volume ; sa conversion
        // parfaite ajoute les 40 restants.
        List<BonusPerformance> ranked = BonusPerformance.rank(List.of(
                new RawBonusRow(BonusKind.REWARD, "a", "Café offert", 100, 100, BigDecimal.TEN, null),
                new RawBonusRow(BonusKind.REWARD, "b", "Bon -10 %", 50, 0, BigDecimal.ZERO, null)));

        assertEquals("a", ranked.get(0).key());
        assertEquals(100d, ranked.get(0).score(), 1e-9);
        // b : volume 50/100 -> 0.6*0.5 = 0.30, conversion nulle -> 30.
        assertEquals(30d, ranked.get(1).score(), 1e-9);
    }

    @Test
    void rank_prefersHighVolumeOverPerfectButMarginalConversion() {
        // Le piège que la pondération 60/40 doit éviter : un bonus octroyé 2 fois et
        // consommé 2 fois ne doit pas coiffer un bonus massivement distribué.
        List<BonusPerformance> ranked = BonusPerformance.rank(List.of(
                new RawBonusRow(BonusKind.REWARD, "niche", "Niche", 2, 2, BigDecimal.ONE, null),
                new RawBonusRow(BonusKind.REWARD, "masse", "Masse", 1000, 600, BigDecimal.TEN, null)));

        assertEquals("masse", ranked.get(0).key());
    }

    @Test
    void rank_carriesUnitOfItsFamily() {
        List<BonusPerformance> points = BonusPerformance.rank(List.of(
                new RawBonusRow(BonusKind.RULE, "r1", "Règle", 5, 5, BigDecimal.TEN, null)));
        List<BonusPerformance> money = BonusPerformance.rank(List.of(
                new RawBonusRow(BonusKind.WALLET_TOPUP, "t1", "MTN", 5, 5, BigDecimal.TEN, "XAF")));

        assertEquals("POINTS", points.get(0).unit());
        assertEquals("CURRENCY", money.get(0).unit());
        assertEquals("XAF", money.get(0).currency());
    }

    @Test
    void rank_handlesEmptyAndZeroVolumeWithoutDividingByZero() {
        assertTrue(BonusPerformance.rank(List.of()).isEmpty());

        List<BonusPerformance> ranked = BonusPerformance.rank(List.of(
                new RawBonusRow(BonusKind.BONIFICATION, "CREDIT", "Crédits", 0, 0, null, null)));

        assertEquals(0d, ranked.get(0).score(), 1e-9);
        assertEquals(0d, ranked.get(0).conversionRate(), 1e-9);
        assertEquals(BigDecimal.ZERO, ranked.get(0).value());
    }

    @Test
    void rawRow_fallsBackToKeyWhenLabelMissing() {
        RawBonusRow row = new RawBonusRow(BonusKind.RULE, "rule-42", null, 1, 1, BigDecimal.ONE, null);

        assertEquals("rule-42", row.label());
    }
}
