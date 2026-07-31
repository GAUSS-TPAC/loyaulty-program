package com.yowyob.loyalty.infrastructure.persistence.analytics;

import com.yowyob.loyalty.domain.analytics.model.AnalyticsScope;
import com.yowyob.loyalty.domain.analytics.model.AnalyticsWindow;
import com.yowyob.loyalty.domain.analytics.model.TimeGranularity;
import io.r2dbc.spi.Row;
import org.springframework.r2dbc.core.DatabaseClient;

import java.math.BigDecimal;

/**
 * Fragments SQL partagés par les agrégations analytiques.
 *
 * <p>Le prédicat de tenant et son binding sont produits par la même paire de méthodes
 * ({@link #tenantPredicate} / {@link #bindScope}) : impossible d'ajouter le
 * {@code :tenantId} à la requête en oubliant de le lier, ou l'inverse — les deux se
 * décident sur le même {@link AnalyticsScope}.
 *
 * <p>Les seules valeurs interpolées dans le SQL sont l'unité de {@code date_trunc}
 * (constante d'énumération) et des noms de colonnes fournis par le code appelant.
 * Tout ce qui vient de l'utilisateur passe par un paramètre nommé.
 */
final class AnalyticsSql {

    private AnalyticsSql() {
    }

    /** Prédicat de tenant, ou chaîne vide en vue plateforme. À concaténer après un {@code WHERE}. */
    static String tenantPredicate(AnalyticsScope scope, String column) {
        return scope.isPlatformWide() ? "" : " AND " + column + " = :tenantId";
    }

    static DatabaseClient.GenericExecuteSpec bindScope(DatabaseClient.GenericExecuteSpec spec,
                                                        AnalyticsScope scope) {
        return scope.isPlatformWide() ? spec : spec.bind("tenantId", scope.tenantId().value());
    }

    static DatabaseClient.GenericExecuteSpec bindWindow(DatabaseClient.GenericExecuteSpec spec,
                                                         AnalyticsWindow window) {
        return spec.bind("from", window.from()).bind("to", window.to());
    }

    /**
     * Expression de tranche temporelle. {@code unit} vient de {@link TimeGranularity},
     * jamais d'une saisie : le premier argument de {@code date_trunc} ne peut pas être
     * un paramètre lié, il doit être interpolé.
     */
    static String bucket(TimeGranularity granularity, String column) {
        return "date_trunc('" + granularity.sqlUnit() + "', " + column + ")";
    }

    /** COUNT/SUM ne renvoient jamais NULL une fois passés par COALESCE, mais les jointures externes si. */
    static long readLong(Row row, String column) {
        Long value = row.get(column, Long.class);
        return value != null ? value : 0L;
    }

    static BigDecimal readAmount(Row row, String column) {
        BigDecimal value = row.get(column, BigDecimal.class);
        return value != null ? value : BigDecimal.ZERO;
    }

    static String readText(Row row, String column, String fallback) {
        String value = row.get(column, String.class);
        return value != null && !value.isBlank() ? value : fallback;
    }
}
