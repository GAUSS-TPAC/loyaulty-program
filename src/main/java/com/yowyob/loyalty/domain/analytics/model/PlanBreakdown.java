package com.yowyob.loyalty.domain.analytics.model;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Répartition du parc d'abonnés par plan tarifaire.
 *
 * <p>{@code monthlyRecurring} normalise l'annuel en mensuel (prix annuel / 12) pour
 * qu'un plan facturé à l'année soit comparable à un plan mensuel sur la même courbe.
 * La devise est portée par ligne : rien n'interdit deux plans dans deux devises, et
 * sommer les montants sans regarder ce champ produirait un total dénué de sens.
 */
public record PlanBreakdown(
        UUID planId,
        String planCode,
        String planName,
        String currency,
        long subscriberCount,
        long activeCount,
        BigDecimal monthlyRecurring
) {}
