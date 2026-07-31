package com.yowyob.loyalty.domain.analytics.model;

import java.math.BigDecimal;

/**
 * Ligne brute renvoyée par une agrégation de bonus, avant tout classement.
 *
 * <p>Forme unique pour les quatre sources : la persistance ne fait que compter
 * ({@code volume}), compter les aboutissements ({@code converted}) et sommer une
 * valeur. Toute la lecture — taux, score, palmarès — est calculée dans le domaine,
 * ce qui garde les requêtes triviales et la logique de classement testable sans base.
 *
 * @param key      identifiant technique du bonus (UUID, code…), stable dans le temps
 * @param label    libellé lisible, éventuellement obsolète si l'objet a été renommé
 * @param volume   nombre d'occurrences (octrois, soumissions, tentatives)
 * @param converted sous-ensemble de {@code volume} ayant abouti (utilisé, réussi, encaissé)
 * @param value    valeur cumulée des occurrences abouties, dans l'unité de {@code kind}
 * @param currency devise de {@code value}, renseignée uniquement pour {@link BonusKind#WALLET_TOPUP}
 */
public record RawBonusRow(
        BonusKind kind,
        String key,
        String label,
        long volume,
        long converted,
        BigDecimal value,
        String currency
) {

    public RawBonusRow {
        if (value == null) {
            value = BigDecimal.ZERO;
        }
        if (label == null || label.isBlank()) {
            label = key;
        }
    }
}
