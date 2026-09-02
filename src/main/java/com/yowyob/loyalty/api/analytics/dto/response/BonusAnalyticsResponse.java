package com.yowyob.loyalty.api.analytics.dto.response;

import com.yowyob.loyalty.domain.analytics.model.BonusAnalytics;
import com.yowyob.loyalty.domain.analytics.model.BonusPerformance;

import java.math.BigDecimal;
import java.util.List;

/**
 * Analyse des meilleurs bonus, un palmarès par source.
 *
 * <p><strong>Les scores ne se comparent qu'à l'intérieur d'un même palmarès.</strong>
 * Récompenses et règles se mesurent en points, le canal partenaire en unités sans devise,
 * les recharges en monnaie : il n'existe aucun taux de change entre ces axes. Le score
 * est normalisé sur le meilleur élément de sa propre famille, ce qui interdit un
 * classement fusionné — d'où quatre listes plutôt qu'une.
 */
public record BonusAnalyticsResponse(
        WindowResponse window,
        List<Bonus> rewards,
        List<Bonus> rules,
        List<Bonus> bonification,
        List<Bonus> topUps,
        List<Bonus> highlights
) {

    /**
     * @param unit     unité de {@code value} : {@code POINTS}, {@code UNITS} (partenaire,
     *                 sans devise) ou {@code CURRENCY} — dans ce dernier cas lire {@code currency}
     * @param score    note sur 100, relative au meilleur élément du même palmarès
     */
    public record Bonus(
            String kind,
            String key,
            String label,
            long volume,
            long converted,
            double conversionRate,
            BigDecimal value,
            String unit,
            String currency,
            double score
    ) {

        static Bonus from(BonusPerformance performance) {
            return new Bonus(
                    performance.kind().name(),
                    performance.key(),
                    performance.label(),
                    performance.volume(),
                    performance.converted(),
                    performance.conversionRate(),
                    performance.value(),
                    performance.unit(),
                    performance.currency(),
                    performance.score());
        }
    }

    public static BonusAnalyticsResponse from(BonusAnalytics analytics) {
        return new BonusAnalyticsResponse(
                WindowResponse.from(analytics.window()),
                map(analytics.rewards()),
                map(analytics.rules()),
                map(analytics.bonification()),
                map(analytics.topUps()),
                map(analytics.highlights())
        );
    }

    private static List<Bonus> map(List<BonusPerformance> rows) {
        return rows.stream().map(Bonus::from).toList();
    }
}
