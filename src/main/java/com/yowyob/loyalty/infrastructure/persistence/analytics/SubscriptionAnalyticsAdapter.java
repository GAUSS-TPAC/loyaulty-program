package com.yowyob.loyalty.infrastructure.persistence.analytics;

import com.yowyob.loyalty.domain.analytics.model.AnalyticsScope;
import com.yowyob.loyalty.domain.analytics.model.AnalyticsWindow;
import com.yowyob.loyalty.domain.analytics.model.PlanBreakdown;
import com.yowyob.loyalty.domain.analytics.model.SubscriptionFlowPoint;
import com.yowyob.loyalty.domain.analytics.model.SubscriptionFunnel;
import com.yowyob.loyalty.domain.analytics.port.out.SubscriptionAnalyticsRepository;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.UUID;

/**
 * Agrégations du flux d'abonnement, en SQL natif.
 *
 * <p>Requêtes écrites à la main plutôt que dérivées de repositories Spring Data : ce
 * sont des agrégations multi-colonnes avec {@code FILTER} et {@code date_trunc}, qu'aucune
 * dérivation de nom de méthode ne sait exprimer.
 *
 * <p><strong>Portée tenant :</strong> {@code tenant_subscriptions.tenant_id} est UNIQUE —
 * un tenant n'a qu'un abonnement. Entonnoir et courbe n'ont donc de relief qu'en vue
 * plateforme ; pour un tenant ils décrivent sa propre trajectoire (0 ou 1 par état).
 */
@Component
public class SubscriptionAnalyticsAdapter implements SubscriptionAnalyticsRepository {

    private final R2dbcEntityTemplate template;

    public SubscriptionAnalyticsAdapter(R2dbcEntityTemplate template) {
        this.template = template;
    }

    @Override
    public Mono<SubscriptionFunnel> funnel(AnalyticsScope scope, AnalyticsWindow window) {
        String predicate = AnalyticsSql.tenantPredicate(scope, "tenant_id");
        // Pas de filtre de fenêtre dans le WHERE : cancelled_in_period doit aussi capter les
        // abonnements nés avant la fenêtre et résiliés pendant. Le bornage est porté par
        // chaque FILTER, colonne par colonne.
        String sql = """
                SELECT
                    COUNT(*) FILTER (WHERE created_at >= :from AND created_at < :to)                            AS created_in_period,
                    COUNT(*) FILTER (WHERE created_at >= :from AND created_at < :to AND status = 'TRIAL')       AS trial_count,
                    COUNT(*) FILTER (WHERE created_at >= :from AND created_at < :to AND status = 'ACTIVE')      AS active_count,
                    COUNT(*) FILTER (WHERE created_at >= :from AND created_at < :to AND status = 'PAST_DUE')    AS past_due_count,
                    COUNT(*) FILTER (WHERE created_at >= :from AND created_at < :to AND status = 'CANCELLED')   AS cancelled_count,
                    COUNT(*) FILTER (WHERE created_at >= :from AND created_at < :to AND status = 'EXPIRED')     AS expired_count,
                    COUNT(*) FILTER (WHERE cancelled_at >= :from AND cancelled_at < :to)                        AS cancelled_in_period
                FROM tenant_subscriptions
                WHERE 1 = 1%s
                """.formatted(predicate);

        var spec = template.getDatabaseClient().sql(sql);
        spec = AnalyticsSql.bindWindow(spec, window);
        spec = AnalyticsSql.bindScope(spec, scope);

        return spec.map((row, meta) -> new SubscriptionFunnel(
                        AnalyticsSql.readLong(row, "created_in_period"),
                        AnalyticsSql.readLong(row, "trial_count"),
                        AnalyticsSql.readLong(row, "active_count"),
                        AnalyticsSql.readLong(row, "past_due_count"),
                        AnalyticsSql.readLong(row, "cancelled_count"),
                        AnalyticsSql.readLong(row, "expired_count"),
                        AnalyticsSql.readLong(row, "cancelled_in_period")))
                .one()
                .defaultIfEmpty(SubscriptionFunnel.empty());
    }

    @Override
    public Flux<SubscriptionFlowPoint> flow(AnalyticsScope scope, AnalyticsWindow window) {
        String predicate = AnalyticsSql.tenantPredicate(scope, "tenant_id");
        // Une même ligne d'abonnement porte deux dates d'intérêt (création, résiliation) qui
        // tombent dans des tranches différentes. L'UNION ALL les réémet comme deux événements
        // distincts, seule façon d'obtenir une courbe où chacun est daté correctement.
        String sql = """
                SELECT bucket,
                       SUM(started)        AS started,
                       SUM(trials_started) AS trials_started,
                       SUM(cancelled)      AS cancelled
                FROM (
                    SELECT %s AS bucket,
                           1 AS started,
                           CASE WHEN trial_end_date IS NOT NULL THEN 1 ELSE 0 END AS trials_started,
                           0 AS cancelled
                    FROM tenant_subscriptions
                    WHERE created_at >= :from AND created_at < :to%s
                    UNION ALL
                    SELECT %s AS bucket, 0, 0, 1
                    FROM tenant_subscriptions
                    WHERE cancelled_at >= :from AND cancelled_at < :to%s
                ) events
                GROUP BY bucket
                ORDER BY bucket
                """.formatted(
                AnalyticsSql.bucket(window.granularity(), "created_at"), predicate,
                AnalyticsSql.bucket(window.granularity(), "cancelled_at"), predicate);

        var spec = template.getDatabaseClient().sql(sql);
        spec = AnalyticsSql.bindWindow(spec, window);
        spec = AnalyticsSql.bindScope(spec, scope);

        return spec.map((row, meta) -> new SubscriptionFlowPoint(
                        row.get("bucket", Instant.class),
                        AnalyticsSql.readLong(row, "started"),
                        AnalyticsSql.readLong(row, "trials_started"),
                        AnalyticsSql.readLong(row, "cancelled")))
                .all();
    }

    @Override
    public Flux<PlanBreakdown> planBreakdown(AnalyticsScope scope) {
        // Le prédicat de tenant vit dans le ON, pas dans le WHERE : dans un WHERE il
        // annulerait la jointure externe et ferait disparaître les plans sans abonné.
        String predicate = AnalyticsSql.tenantPredicate(scope, "s.tenant_id");
        String sql = """
                SELECT p.id                AS plan_id,
                       p.code              AS plan_code,
                       p.name              AS plan_name,
                       p.currency          AS currency,
                       COUNT(s.id)                                        AS subscriber_count,
                       COUNT(s.id) FILTER (WHERE s.status = 'ACTIVE')      AS active_count,
                       COALESCE(SUM(
                           CASE WHEN s.status IN ('ACTIVE', 'PAST_DUE')
                                THEN CASE WHEN s.billing_cycle = 'YEARLY'
                                          THEN ROUND(p.price_yearly / 12, 2)
                                          ELSE p.price_monthly END
                                ELSE 0 END
                       ), 0)                                              AS monthly_recurring
                FROM subscription_plans p
                LEFT JOIN tenant_subscriptions s ON s.plan_id = p.id%s
                GROUP BY p.id, p.code, p.name, p.currency
                ORDER BY monthly_recurring DESC, subscriber_count DESC
                """.formatted(predicate);

        var spec = template.getDatabaseClient().sql(sql);
        spec = AnalyticsSql.bindScope(spec, scope);

        return spec.map((row, meta) -> new PlanBreakdown(
                        row.get("plan_id", UUID.class),
                        AnalyticsSql.readText(row, "plan_code", "—"),
                        AnalyticsSql.readText(row, "plan_name", "—"),
                        AnalyticsSql.readText(row, "currency", "XAF"),
                        AnalyticsSql.readLong(row, "subscriber_count"),
                        AnalyticsSql.readLong(row, "active_count"),
                        AnalyticsSql.readAmount(row, "monthly_recurring")))
                .all();
    }
}
