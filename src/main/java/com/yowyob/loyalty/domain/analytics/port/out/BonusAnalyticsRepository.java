package com.yowyob.loyalty.domain.analytics.port.out;

import com.yowyob.loyalty.domain.analytics.model.AnalyticsScope;
import com.yowyob.loyalty.domain.analytics.model.AnalyticsWindow;
import com.yowyob.loyalty.domain.analytics.model.RawBonusRow;
import reactor.core.publisher.Flux;

/**
 * Agrégations des quatre sources de bonus, toutes réduites à la même forme brute
 * ({@link RawBonusRow}) : comptage, aboutissements, valeur cumulée.
 *
 * <p>Le tri et la notation restent hors de la persistance — c'est le domaine qui
 * classe (voir {@code BonusPerformance#rank}). {@code limit} borne malgré tout chaque
 * requête : un tenant avec des milliers de récompenses ne doit pas rapatrier tout son
 * catalogue pour afficher un top 10.
 */
public interface BonusAnalyticsRepository {

    /** Récompenses du catalogue : octrois, consommations, points dépensés. */
    Flux<RawBonusRow> topRewards(AnalyticsScope scope, AnalyticsWindow window, int limit);

    /** Règles du moteur loyalty : mouvements de points attribués à chaque règle. */
    Flux<RawBonusRow> topRules(AnalyticsScope scope, AnalyticsWindow window, int limit);

    /** Canal partenaire Bonification, ventilé crédit/débit. */
    Flux<RawBonusRow> bonificationBreakdown(AnalyticsScope scope, AnalyticsWindow window);

    /** Recharges wallet abouties, ventilées par PSP et moyen de paiement. */
    Flux<RawBonusRow> topUpBreakdown(AnalyticsScope scope, AnalyticsWindow window, int limit);
}
