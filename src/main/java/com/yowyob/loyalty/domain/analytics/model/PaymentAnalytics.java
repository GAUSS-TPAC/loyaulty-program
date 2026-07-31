package com.yowyob.loyalty.domain.analytics.model;

import java.util.List;

/**
 * Vue paiement complète : encaissement wallet (passerelle) d'un côté, facturation
 * des abonnements de l'autre, chacun avec ses KPI, sa courbe et ses ventilations.
 *
 * <p>Les deux flux restent séparés à tous les niveaux : ce sont des mouvements d'argent
 * de nature différente (fonds de membres vs revenu de la plateforme), et les additionner
 * produirait un chiffre d'affaires fictif.
 */
public record PaymentAnalytics(
        AnalyticsWindow window,
        List<GatewayStats> gateway,
        List<PaymentSeriesPoint> gatewaySeries,
        List<PaymentBreakdown> gatewayBreakdown,
        List<BillingStats> billing,
        List<BillingSeriesPoint> billingSeries
) {}
