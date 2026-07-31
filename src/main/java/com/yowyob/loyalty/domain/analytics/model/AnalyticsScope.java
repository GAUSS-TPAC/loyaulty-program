package com.yowyob.loyalty.domain.analytics.model;

import com.yowyob.loyalty.domain.shared.model.TenantId;

import java.util.Optional;

/**
 * Périmètre d'une agrégation : un tenant, ou la plateforme entière.
 *
 * <p>Les deux consoles (console tenant et console plateforme) posent exactement les
 * mêmes questions sur les mêmes tables, à un prédicat près. Porter ce prédicat dans
 * un type explicite évite de dupliquer chaque requête d'agrégation en deux variantes,
 * et rend impossible l'oubli silencieux du filtre de tenant : une requête de tenant se
 * construit forcément à partir d'un {@code AnalyticsScope}, jamais d'un {@code null}.
 */
public record AnalyticsScope(TenantId tenantId) {

    /** Vue cross-tenant, réservée à la console plateforme. */
    public static AnalyticsScope platformWide() {
        return new AnalyticsScope(null);
    }

    public static AnalyticsScope ofTenant(TenantId tenantId) {
        if (tenantId == null) {
            throw new IllegalArgumentException("tenantId requis pour un périmètre de tenant");
        }
        return new AnalyticsScope(tenantId);
    }

    public boolean isPlatformWide() {
        return tenantId == null;
    }

    public Optional<TenantId> tenant() {
        return Optional.ofNullable(tenantId);
    }
}
