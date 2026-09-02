package com.yowyob.loyalty.domain.analytics.model;

import com.yowyob.loyalty.domain.shared.exception.DomainValidationException;

/**
 * Origine d'un bonus mesuré.
 *
 * <p>Chaque famille a sa propre unité de valeur ({@link #valueUnit()}) : des points
 * loyalty, des unités partenaires sans devise, ou de la monnaie. Ce type existe
 * précisément pour empêcher de les additionner par inadvertance dans un classement
 * unique — voir {@link BonusPerformance#score()}.
 */
public enum BonusKind {

    /** Récompense du catalogue, mesurée en points dépensés par les membres. */
    REWARD("POINTS"),

    /** Règle du moteur loyalty, mesurée en points distribués. */
    RULE("POINTS"),

    /** Transaction poussée vers l'API partenaire Bonification — montant sans devise. */
    BONIFICATION("UNITS"),

    /** Recharge wallet aboutie via la passerelle — montant monétaire, devise portée par la ligne. */
    WALLET_TOPUP("CURRENCY");

    private final String valueUnit;

    BonusKind(String valueUnit) {
        this.valueUnit = valueUnit;
    }

    /** Unité de {@code value}. « CURRENCY » signifie : lire la devise sur la ligne. */
    public String valueUnit() {
        return valueUnit;
    }

    public static BonusKind parse(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new DomainValidationException("Type de bonus requis");
        }
        try {
            return valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new DomainValidationException("Type de bonus inconnu : " + raw);
        }
    }
}
