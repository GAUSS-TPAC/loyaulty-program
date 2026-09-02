package com.yowyob.loyalty.infrastructure.persistence.analytics;

import com.yowyob.loyalty.domain.analytics.model.AnalyticsScope;
import com.yowyob.loyalty.domain.analytics.model.AnalyticsWindow;
import com.yowyob.loyalty.domain.analytics.model.BillingSeriesPoint;
import com.yowyob.loyalty.domain.analytics.model.BillingStats;
import com.yowyob.loyalty.domain.analytics.model.GatewayStats;
import com.yowyob.loyalty.domain.analytics.model.PaymentBreakdown;
import com.yowyob.loyalty.domain.analytics.model.PaymentSeriesPoint;
import com.yowyob.loyalty.domain.analytics.port.out.PaymentAnalyticsRepository;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.time.Instant;

/**
 * Agrégations des deux flux monétaires.
 *
 * <p>Toutes les agrégations de montants sont groupées par devise. {@code payment_requests}
 * et {@code invoice_records} portent chacune leur devise ligne à ligne et rien n'impose
 * qu'elle soit unique : un {@code SUM} global produirait un total additionnant des XAF
 * et des EUR.
 *
 * <p>Les recharges sont filtrées sur {@code direction = 'INBOUND'} : les retraits
 * transitent par la même table mais ne sont pas des encaissements.
 */
@Component
public class PaymentAnalyticsAdapter implements PaymentAnalyticsRepository {

    /** Statuts non tranchés de la passerelle — regroupés côté exploitation. */
    private static final String PENDING_STATUSES = "('INITIATED', 'PENDING')";

    private final R2dbcEntityTemplate template;

    public PaymentAnalyticsAdapter(R2dbcEntityTemplate template) {
        this.template = template;
    }

    @Override
    public Flux<GatewayStats> gatewayStats(AnalyticsScope scope, AnalyticsWindow window) {
        String predicate = AnalyticsSql.tenantPredicate(scope, "tenant_id");
        String sql = """
                SELECT currency,
                       COUNT(*)                                                        AS initiated,
                       COUNT(*) FILTER (WHERE status = 'COMPLETED')                    AS succeeded,
                       COUNT(*) FILTER (WHERE status IN ('FAILED', 'CANCELLED', 'EXPIRED')) AS failed,
                       COUNT(*) FILTER (WHERE status IN %s)                            AS pending,
                       COALESCE(SUM(amount) FILTER (WHERE status = 'COMPLETED'), 0)    AS collected_amount,
                       COALESCE(SUM(amount), 0)                                        AS attempted_amount
                FROM payment_requests
                WHERE direction = 'INBOUND'
                  AND initiated_at >= :from AND initiated_at < :to%s
                GROUP BY currency
                ORDER BY collected_amount DESC
                """.formatted(PENDING_STATUSES, predicate);

        return execute(sql, scope, window)
                .map((row, meta) -> new GatewayStats(
                        AnalyticsSql.readText(row, "currency", "XAF"),
                        AnalyticsSql.readLong(row, "initiated"),
                        AnalyticsSql.readLong(row, "succeeded"),
                        AnalyticsSql.readLong(row, "failed"),
                        AnalyticsSql.readLong(row, "pending"),
                        AnalyticsSql.readAmount(row, "collected_amount"),
                        AnalyticsSql.readAmount(row, "attempted_amount")))
                .all();
    }

    @Override
    public Flux<PaymentSeriesPoint> gatewaySeries(AnalyticsScope scope, AnalyticsWindow window) {
        String predicate = AnalyticsSql.tenantPredicate(scope, "tenant_id");
        // Tout est daté sur initiated_at, y compris le montant encaissé : une recharge lancée
        // lundi et confirmée mardi reste dans la tranche du lundi, ce qui rend le taux de
        // succès lisible tranche par tranche sans mélanger deux cohortes.
        String sql = """
                SELECT %s AS bucket,
                       currency,
                       COUNT(*)                                                        AS attempts,
                       COUNT(*) FILTER (WHERE status = 'COMPLETED')                    AS succeeded,
                       COUNT(*) FILTER (WHERE status IN ('FAILED', 'CANCELLED', 'EXPIRED')) AS failed,
                       COALESCE(SUM(amount) FILTER (WHERE status = 'COMPLETED'), 0)    AS collected_amount
                FROM payment_requests
                WHERE direction = 'INBOUND'
                  AND initiated_at >= :from AND initiated_at < :to%s
                GROUP BY bucket, currency
                ORDER BY bucket, currency
                """.formatted(AnalyticsSql.bucket(window.granularity(), "initiated_at"), predicate);

        return execute(sql, scope, window)
                .map((row, meta) -> new PaymentSeriesPoint(
                        row.get("bucket", Instant.class),
                        AnalyticsSql.readText(row, "currency", "XAF"),
                        AnalyticsSql.readLong(row, "attempts"),
                        AnalyticsSql.readLong(row, "succeeded"),
                        AnalyticsSql.readLong(row, "failed"),
                        AnalyticsSql.readAmount(row, "collected_amount")))
                .all();
    }

    @Override
    public Flux<PaymentBreakdown> gatewayBreakdown(AnalyticsScope scope, AnalyticsWindow window) {
        String predicate = AnalyticsSql.tenantPredicate(scope, "tenant_id");
        // Deux axes en une requête : l'UNION ALL évite un aller-retour supplémentaire et
        // rend les deux ventilations rigoureusement cohérentes (mêmes bornes, même instant).
        String sql = """
                SELECT 'METHOD' AS dimension,
                       COALESCE(method, 'INCONNU') AS dimension_key,
                       currency,
                       COUNT(*)                                                        AS attempts,
                       COUNT(*) FILTER (WHERE status = 'COMPLETED')                    AS succeeded,
                       COUNT(*) FILTER (WHERE status IN ('FAILED', 'CANCELLED', 'EXPIRED')) AS failed,
                       COALESCE(SUM(amount) FILTER (WHERE status = 'COMPLETED'), 0)    AS collected_amount
                FROM payment_requests
                WHERE direction = 'INBOUND'
                  AND initiated_at >= :from AND initiated_at < :to%s
                GROUP BY COALESCE(method, 'INCONNU'), currency
                UNION ALL
                SELECT 'PROVIDER' AS dimension,
                       provider AS dimension_key,
                       currency,
                       COUNT(*),
                       COUNT(*) FILTER (WHERE status = 'COMPLETED'),
                       COUNT(*) FILTER (WHERE status IN ('FAILED', 'CANCELLED', 'EXPIRED')),
                       COALESCE(SUM(amount) FILTER (WHERE status = 'COMPLETED'), 0)
                FROM payment_requests
                WHERE direction = 'INBOUND'
                  AND initiated_at >= :from AND initiated_at < :to%s
                GROUP BY provider, currency
                ORDER BY dimension, collected_amount DESC
                """.formatted(predicate, predicate);

        return execute(sql, scope, window)
                .map((row, meta) -> new PaymentBreakdown(
                        AnalyticsSql.readText(row, "dimension", "METHOD"),
                        AnalyticsSql.readText(row, "dimension_key", "INCONNU"),
                        AnalyticsSql.readText(row, "currency", "XAF"),
                        AnalyticsSql.readLong(row, "attempts"),
                        AnalyticsSql.readLong(row, "succeeded"),
                        AnalyticsSql.readLong(row, "failed"),
                        AnalyticsSql.readAmount(row, "collected_amount")))
                .all();
    }

    @Override
    public Flux<BillingStats> billingStats(AnalyticsScope scope, AnalyticsWindow window) {
        String predicate = AnalyticsSql.tenantPredicate(scope, "tenant_id");
        // collected_amount se date sur paid_at, pas sur created_at : c'est le recouvrement
        // réel de la fenêtre, factures antérieures comprises.
        String sql = """
                SELECT currency,
                       COUNT(*) FILTER (WHERE created_at >= :from AND created_at < :to)                        AS issued,
                       COUNT(*) FILTER (WHERE paid_at   >= :from AND paid_at   < :to)                          AS paid,
                       COUNT(*) FILTER (WHERE created_at >= :from AND created_at < :to AND status = 'PENDING') AS pending,
                       COUNT(*) FILTER (WHERE created_at >= :from AND created_at < :to AND status = 'FAILED')  AS failed,
                       COALESCE(SUM(amount) FILTER (WHERE created_at >= :from AND created_at < :to), 0)        AS billed_amount,
                       COALESCE(SUM(amount) FILTER (WHERE paid_at   >= :from AND paid_at   < :to), 0)          AS collected_amount
                FROM invoice_records
                WHERE 1 = 1%s
                GROUP BY currency
                HAVING COUNT(*) FILTER (WHERE created_at >= :from AND created_at < :to) > 0
                    OR COUNT(*) FILTER (WHERE paid_at   >= :from AND paid_at   < :to) > 0
                ORDER BY billed_amount DESC
                """.formatted(predicate);

        return execute(sql, scope, window)
                .map((row, meta) -> new BillingStats(
                        AnalyticsSql.readText(row, "currency", "XAF"),
                        AnalyticsSql.readLong(row, "issued"),
                        AnalyticsSql.readLong(row, "paid"),
                        AnalyticsSql.readLong(row, "pending"),
                        AnalyticsSql.readLong(row, "failed"),
                        AnalyticsSql.readAmount(row, "billed_amount"),
                        AnalyticsSql.readAmount(row, "collected_amount")))
                .all();
    }

    @Override
    public Flux<BillingSeriesPoint> billingSeries(AnalyticsScope scope, AnalyticsWindow window) {
        String predicate = AnalyticsSql.tenantPredicate(scope, "tenant_id");
        // Émission et encaissement sont deux événements datés différemment : une facture de
        // janvier payée en mars doit apparaître dans les deux tranches concernées, d'où
        // l'UNION ALL plutôt qu'un simple GROUP BY sur created_at.
        String sql = """
                SELECT bucket, currency,
                       SUM(issued)           AS issued,
                       SUM(paid)             AS paid,
                       SUM(billed_amount)    AS billed_amount,
                       SUM(collected_amount) AS collected_amount
                FROM (
                    SELECT %s AS bucket, currency,
                           1 AS issued, 0 AS paid,
                           amount AS billed_amount, 0 AS collected_amount
                    FROM invoice_records
                    WHERE created_at >= :from AND created_at < :to%s
                    UNION ALL
                    SELECT %s AS bucket, currency,
                           0, 1,
                           0, amount
                    FROM invoice_records
                    WHERE paid_at >= :from AND paid_at < :to%s
                ) events
                GROUP BY bucket, currency
                ORDER BY bucket, currency
                """.formatted(
                AnalyticsSql.bucket(window.granularity(), "created_at"), predicate,
                AnalyticsSql.bucket(window.granularity(), "paid_at"), predicate);

        return execute(sql, scope, window)
                .map((row, meta) -> new BillingSeriesPoint(
                        row.get("bucket", Instant.class),
                        AnalyticsSql.readText(row, "currency", "XAF"),
                        AnalyticsSql.readLong(row, "issued"),
                        AnalyticsSql.readLong(row, "paid"),
                        AnalyticsSql.readAmount(row, "billed_amount"),
                        AnalyticsSql.readAmount(row, "collected_amount")))
                .all();
    }

    private org.springframework.r2dbc.core.DatabaseClient.GenericExecuteSpec execute(
            String sql, AnalyticsScope scope, AnalyticsWindow window) {
        var spec = template.getDatabaseClient().sql(sql);
        spec = AnalyticsSql.bindWindow(spec, window);
        return AnalyticsSql.bindScope(spec, scope);
    }
}
