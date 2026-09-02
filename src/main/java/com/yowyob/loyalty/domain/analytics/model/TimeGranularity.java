package com.yowyob.loyalty.domain.analytics.model;

import com.yowyob.loyalty.domain.shared.exception.DomainValidationException;

import java.time.Duration;

/**
 * Pas d'échantillonnage des courbes.
 *
 * <p>{@link #sqlUnit()} est destiné à {@code date_trunc} : la valeur est une constante
 * du domaine, jamais une saisie utilisateur, ce qui garantit qu'aucune chaîne arbitraire
 * ne peut atteindre la requête (le premier argument de {@code date_trunc} n'est pas
 * paramétrable, il doit être interpolé).
 */
public enum TimeGranularity {

    DAY("day", Duration.ofDays(1)),
    WEEK("week", Duration.ofDays(7)),
    MONTH("month", Duration.ofDays(30));

    private final String sqlUnit;
    private final Duration approximateStep;

    TimeGranularity(String sqlUnit, Duration approximateStep) {
        this.sqlUnit = sqlUnit;
        this.approximateStep = approximateStep;
    }

    public String sqlUnit() {
        return sqlUnit;
    }

    public Duration approximateStep() {
        return approximateStep;
    }

    public static TimeGranularity parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return DAY;
        }
        try {
            return valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new DomainValidationException(
                    "Granularité invalide : " + raw + " (attendu DAY, WEEK ou MONTH)");
        }
    }
}
