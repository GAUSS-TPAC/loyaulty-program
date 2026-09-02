package com.yowyob.loyalty.domain.analytics.model;

import java.math.BigDecimal;
import java.util.List;

/**
 * Ligne classée d'un palmarès de bonus.
 *
 * <p><strong>Le score est relatif à la famille, jamais absolu.</strong> Une récompense
 * se mesure en points, une transaction bonification en unités partenaires sans devise,
 * une recharge en francs : il n'existe aucun taux de change entre ces axes. Le score
 * est donc normalisé <em>à l'intérieur</em> d'un {@link BonusKind}, sur le meilleur
 * élément de cette famille, et deux scores de familles différentes ne se comparent pas.
 * C'est aussi pourquoi {@link BonusAnalytics} expose quatre palmarès et non un seul.
 *
 * @param score note sur 100, relative au meilleur élément de la même famille
 */
public record BonusPerformance(
        BonusKind kind,
        String key,
        String label,
        long volume,
        long converted,
        double conversionRate,
        BigDecimal value,
        String unit,
        String currency,
        double score
) {

    /**
     * Pondération volume/conversion du score.
     *
     * <p>60/40 en faveur du volume : un bonus octroyé trois fois et consommé trois fois
     * affiche une conversion parfaite sans rien prouver, alors qu'un bonus massivement
     * distribué et à moitié consommé pèse réellement sur l'activité. La conversion garde
     * un poids substantiel pour que le volume seul ne suffise pas à dominer le classement.
     */
    private static final double VOLUME_WEIGHT = 0.6d;
    private static final double CONVERSION_WEIGHT = 0.4d;

    /**
     * Classe les lignes d'une même famille, meilleur score d'abord.
     *
     * <p>Le volume est rapporté au maximum de la famille, ce qui rend le score
     * indépendant de l'unité de valeur et donc calculable pour les quatre sources
     * avec la même formule.
     */
    public static List<BonusPerformance> rank(List<RawBonusRow> rows) {
        long maxVolume = rows.stream().mapToLong(RawBonusRow::volume).max().orElse(0L);
        return rows.stream()
                .map(row -> from(row, maxVolume))
                .sorted((a, b) -> Double.compare(b.score, a.score))
                .toList();
    }

    private static BonusPerformance from(RawBonusRow row, long maxVolume) {
        double conversion = row.volume() == 0 ? 0d : (double) row.converted() / row.volume();
        double volumeRatio = maxVolume == 0 ? 0d : (double) row.volume() / maxVolume;
        double score = (VOLUME_WEIGHT * volumeRatio + CONVERSION_WEIGHT * conversion) * 100d;
        return new BonusPerformance(
                row.kind(),
                row.key(),
                row.label(),
                row.volume(),
                row.converted(),
                conversion,
                row.value(),
                row.kind().valueUnit(),
                row.currency(),
                Math.round(score * 100d) / 100d
        );
    }
}
