package com.yowyob.loyalty.infrastructure.persistence.analytics;

import com.yowyob.loyalty.domain.analytics.model.AnalyticsScope;
import com.yowyob.loyalty.domain.analytics.model.AnalyticsWindow;
import com.yowyob.loyalty.domain.analytics.model.BonusKind;
import com.yowyob.loyalty.domain.analytics.model.RawBonusRow;
import com.yowyob.loyalty.domain.analytics.port.out.BonusAnalyticsRepository;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

/**
 * Agrégations des quatre sources de bonus.
 *
 * <p>Chaque requête se réduit au même triplet — volume, aboutissements, valeur — pour
 * que le classement et la notation vivent entièrement dans le domaine. Le tri SQL ne
 * sert qu'à choisir <em>quelles</em> lignes rapatrier sous {@code LIMIT} ; le tri
 * définitif est refait sur le score.
 */
@Component
public class BonusAnalyticsAdapter implements BonusAnalyticsRepository {

    private final R2dbcEntityTemplate template;

    public BonusAnalyticsAdapter(R2dbcEntityTemplate template) {
        this.template = template;
    }

    @Override
    public Flux<RawBonusRow> topRewards(AnalyticsScope scope, AnalyticsWindow window, int limit) {
        String predicate = AnalyticsSql.tenantPredicate(scope, "g.tenant_id");
        // La valeur est le coût en points des octrois effectivement consommés : ce que les
        // membres ont réellement dépensé, pas ce qui a été distribué sans être utilisé.
        String sql = """
                SELECT r.id::text                                                        AS bonus_key,
                       r.name                                                            AS label,
                       COUNT(g.id)                                                       AS volume,
                       COUNT(g.id) FILTER (WHERE g.status = 'USED')                      AS converted,
                       COALESCE(SUM(r.cost_in_points) FILTER (WHERE g.status = 'USED'), 0) AS value
                FROM reward_grants g
                JOIN rewards r ON r.id = g.reward_id
                WHERE g.granted_at >= :from AND g.granted_at < :to%s
                GROUP BY r.id, r.name
                ORDER BY volume DESC, value DESC
                LIMIT :limit
                """.formatted(predicate);

        return query(sql, scope, window, limit, BonusKind.REWARD);
    }

    @Override
    public Flux<RawBonusRow> topRules(AnalyticsScope scope, AnalyticsWindow window, int limit) {
        String predicate = AnalyticsSql.tenantPredicate(scope, "pt.tenant_id");
        // Jointure externe sur rules : une règle supprimée garde ses mouvements de points
        // historiques, les exclure amputerait le total distribué sans prévenir.
        String sql = """
                SELECT pt.rule_id::text                                        AS bonus_key,
                       COALESCE(ru.name, 'Règle supprimée')                    AS label,
                       COUNT(*)                                                AS volume,
                       COUNT(*) FILTER (WHERE pt.amount > 0)                   AS converted,
                       COALESCE(SUM(pt.amount) FILTER (WHERE pt.amount > 0), 0) AS value
                FROM points_transactions pt
                LEFT JOIN rules ru ON ru.id = pt.rule_id
                WHERE pt.rule_id IS NOT NULL
                  AND pt.created_at >= :from AND pt.created_at < :to%s
                GROUP BY pt.rule_id, ru.name
                ORDER BY value DESC, volume DESC
                LIMIT :limit
                """.formatted(predicate);

        return query(sql, scope, window, limit, BonusKind.RULE);
    }

    @Override
    public Flux<RawBonusRow> bonificationBreakdown(AnalyticsScope scope, AnalyticsWindow window) {
        String predicate = AnalyticsSql.tenantPredicate(scope, "tenant_id");
        // Le canal partenaire n'expose aucune notion de « produit » à classer : la seule
        // ventilation qui porte du sens est le sens du mouvement, avec son taux de succès.
        String sql = """
                SELECT CASE WHEN debit THEN 'DEBIT' ELSE 'CREDIT' END      AS bonus_key,
                       CASE WHEN debit THEN 'Bonification — débits'
                                       ELSE 'Bonification — crédits' END   AS label,
                       COUNT(*)                                            AS volume,
                       COUNT(*) FILTER (WHERE succeeded)                   AS converted,
                       COALESCE(SUM(amount) FILTER (WHERE succeeded), 0)   AS value
                FROM bonification_transactions
                WHERE submitted_at >= :from AND submitted_at < :to%s
                GROUP BY debit
                ORDER BY value DESC
                """.formatted(predicate);

        var spec = template.getDatabaseClient().sql(sql);
        spec = AnalyticsSql.bindWindow(spec, window);
        spec = AnalyticsSql.bindScope(spec, scope);

        return spec.map((row, meta) -> new RawBonusRow(
                        BonusKind.BONIFICATION,
                        AnalyticsSql.readText(row, "bonus_key", "CREDIT"),
                        AnalyticsSql.readText(row, "label", null),
                        AnalyticsSql.readLong(row, "volume"),
                        AnalyticsSql.readLong(row, "converted"),
                        AnalyticsSql.readAmount(row, "value"),
                        null))
                .all();
    }

    @Override
    public Flux<RawBonusRow> topUpBreakdown(AnalyticsScope scope, AnalyticsWindow window, int limit) {
        String predicate = AnalyticsSql.tenantPredicate(scope, "tenant_id");
        // Groupé aussi par devise : sans elle, deux recharges de 10 000 XAF et 10 000 EUR
        // se retrouveraient dans la même ligne de palmarès.
        String sql = """
                SELECT provider || ' / ' || COALESCE(method, 'INCONNU') || ' / ' || currency AS bonus_key,
                       provider || ' · ' || COALESCE(method, 'moyen inconnu')                AS label,
                       currency                                                              AS currency,
                       COUNT(*)                                                              AS volume,
                       COUNT(*) FILTER (WHERE status = 'COMPLETED')                          AS converted,
                       COALESCE(SUM(amount) FILTER (WHERE status = 'COMPLETED'), 0)          AS value
                FROM payment_requests
                WHERE direction = 'INBOUND'
                  AND initiated_at >= :from AND initiated_at < :to%s
                -- Regroupement sur la colonne brute, pas sur le COALESCE : deux libellés
                -- de repli différents apparaissent dans le SELECT (clé technique et
                -- libellé lisible), et PostgreSQL n'accepte une colonne nue que si le
                -- GROUP BY porte sur elle et non sur une expression qui l'enveloppe.
                -- Les NULL se regroupent naturellement ensemble.
                GROUP BY provider, method, currency
                ORDER BY value DESC, volume DESC
                LIMIT :limit
                """.formatted(predicate);

        var spec = template.getDatabaseClient().sql(sql);
        spec = AnalyticsSql.bindWindow(spec, window);
        spec = AnalyticsSql.bindScope(spec, scope);
        spec = spec.bind("limit", limit);

        return spec.map((row, meta) -> new RawBonusRow(
                        BonusKind.WALLET_TOPUP,
                        AnalyticsSql.readText(row, "bonus_key", "INCONNU"),
                        AnalyticsSql.readText(row, "label", null),
                        AnalyticsSql.readLong(row, "volume"),
                        AnalyticsSql.readLong(row, "converted"),
                        AnalyticsSql.readAmount(row, "value"),
                        AnalyticsSql.readText(row, "currency", "XAF")))
                .all();
    }

    /** Lecture commune aux agrégations exprimées en points (récompenses, règles). */
    private Flux<RawBonusRow> query(String sql, AnalyticsScope scope, AnalyticsWindow window,
                                     int limit, BonusKind kind) {
        var spec = template.getDatabaseClient().sql(sql);
        spec = AnalyticsSql.bindWindow(spec, window);
        spec = AnalyticsSql.bindScope(spec, scope);
        spec = spec.bind("limit", limit);

        return spec.map((row, meta) -> new RawBonusRow(
                        kind,
                        AnalyticsSql.readText(row, "bonus_key", "—"),
                        AnalyticsSql.readText(row, "label", null),
                        AnalyticsSql.readLong(row, "volume"),
                        AnalyticsSql.readLong(row, "converted"),
                        AnalyticsSql.readAmount(row, "value"),
                        null))
                .all();
    }
}
