package com.yowyob.loyalty.api.analytics.dto.response;

import com.yowyob.loyalty.domain.analytics.model.PaymentAnalytics;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Statistiques et courbes de paiement.
 *
 * <p>Deux blocs strictement séparés : {@code gateway} décrit les recharges wallet des
 * membres passant par la passerelle, {@code billing} le revenu que la plateforme facture
 * à ses tenants. Ce sont des flux d'argent distincts ; les additionner produirait un
 * chiffre d'affaires fictif.
 *
 * <p>Chaque bloc est une liste indexée par devise, jamais un scalaire : rien n'impose
 * une devise unique et un total inter-devises n'aurait pas de sens.
 */
public record PaymentAnalyticsResponse(
        WindowResponse window,
        List<Gateway> gateway,
        List<GatewayPoint> gatewaySeries,
        List<Breakdown> gatewayBreakdown,
        List<Billing> billing,
        List<BillingPoint> billingSeries
) {

    /** @param successRate rapporté aux seules tentatives tranchées, hors recharges en attente */
    public record Gateway(
            String currency,
            long initiated,
            long succeeded,
            long failed,
            long pending,
            BigDecimal collectedAmount,
            BigDecimal attemptedAmount,
            double successRate,
            BigDecimal averageTicket
    ) {}

    public record GatewayPoint(
            Instant bucket,
            String currency,
            long attempts,
            long succeeded,
            long failed,
            BigDecimal collectedAmount,
            double successRate
    ) {}

    /** @param dimension axe de ventilation : {@code METHOD} ou {@code PROVIDER} */
    public record Breakdown(
            String dimension,
            String key,
            String currency,
            long attempts,
            long succeeded,
            long failed,
            BigDecimal collectedAmount,
            double successRate
    ) {}

    public record Billing(
            String currency,
            long issued,
            long paid,
            long pending,
            long failed,
            BigDecimal billedAmount,
            BigDecimal collectedAmount,
            BigDecimal outstandingAmount,
            double collectionRate
    ) {}

    public record BillingPoint(
            Instant bucket,
            String currency,
            long issued,
            long paid,
            BigDecimal billedAmount,
            BigDecimal collectedAmount
    ) {}

    public static PaymentAnalyticsResponse from(PaymentAnalytics analytics) {
        return new PaymentAnalyticsResponse(
                WindowResponse.from(analytics.window()),
                analytics.gateway().stream()
                        .map(stats -> new Gateway(
                                stats.currency(),
                                stats.initiated(),
                                stats.succeeded(),
                                stats.failed(),
                                stats.pending(),
                                stats.collectedAmount(),
                                stats.attemptedAmount(),
                                stats.successRate(),
                                stats.averageTicket()))
                        .toList(),
                analytics.gatewaySeries().stream()
                        .map(point -> new GatewayPoint(
                                point.bucket(),
                                point.currency(),
                                point.attempts(),
                                point.succeeded(),
                                point.failed(),
                                point.collectedAmount(),
                                point.successRate()))
                        .toList(),
                analytics.gatewayBreakdown().stream()
                        .map(row -> new Breakdown(
                                row.dimension(),
                                row.key(),
                                row.currency(),
                                row.attempts(),
                                row.succeeded(),
                                row.failed(),
                                row.collectedAmount(),
                                row.successRate()))
                        .toList(),
                analytics.billing().stream()
                        .map(stats -> new Billing(
                                stats.currency(),
                                stats.issued(),
                                stats.paid(),
                                stats.pending(),
                                stats.failed(),
                                stats.billedAmount(),
                                stats.collectedAmount(),
                                stats.outstandingAmount(),
                                stats.collectionRate()))
                        .toList(),
                analytics.billingSeries().stream()
                        .map(point -> new BillingPoint(
                                point.bucket(),
                                point.currency(),
                                point.issued(),
                                point.paid(),
                                point.billedAmount(),
                                point.collectedAmount()))
                        .toList()
        );
    }
}
