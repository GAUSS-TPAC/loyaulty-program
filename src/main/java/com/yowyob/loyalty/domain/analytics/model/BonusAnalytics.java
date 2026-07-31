package com.yowyob.loyalty.domain.analytics.model;

import java.util.List;

/**
 * Analyse des meilleurs bonus, un palmarès par source.
 *
 * <p>Quatre listes plutôt qu'une : les scores sont normalisés par famille (voir
 * {@link BonusPerformance}), donc un classement fusionné laisserait croire qu'une
 * récompense à 80 « vaut mieux » qu'une recharge à 70, ce qui n'a aucun sens. La
 * console affiche quatre colonnes ; c'est le lecteur qui arbitre entre les canaux.
 *
 * @param rewards       récompenses du catalogue, classées sur les octrois et leur consommation
 * @param rules         règles du moteur loyalty, classées sur les points distribués
 * @param bonification  canal partenaire, ventilé crédit/débit avec son taux de succès
 * @param topUps        recharges wallet, ventilées par PSP et moyen de paiement
 */
public record BonusAnalytics(
        AnalyticsWindow window,
        List<BonusPerformance> rewards,
        List<BonusPerformance> rules,
        List<BonusPerformance> bonification,
        List<BonusPerformance> topUps
) {

    /**
     * Meilleur élément de chaque famille — la ligne d'accroche d'un tableau de bord,
     * sans prétendre les départager entre elles.
     */
    public List<BonusPerformance> highlights() {
        return List.of(rewards, rules, bonification, topUps).stream()
                .filter(list -> !list.isEmpty())
                .map(List::getFirst)
                .toList();
    }
}
