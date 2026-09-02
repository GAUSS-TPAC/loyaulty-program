package com.yowyob.loyalty.api.analytics;

import com.yowyob.loyalty.domain.analytics.model.AnalyticsWindow;
import com.yowyob.loyalty.domain.analytics.model.TimeGranularity;

import java.time.Instant;

/**
 * Traduction des paramètres de requête en fenêtre d'analyse.
 *
 * <p>Partagé par la console tenant et la console plateforme : les deux exposent les
 * mêmes paramètres, et un défaut qui divergerait entre les deux surfaces produirait des
 * chiffres différents pour la même question.
 */
public final class AnalyticsQuery {

    private AnalyticsQuery() {
    }

    /**
     * Bornes absentes complétées côté serveur (30 derniers jours, pas journalier) : un
     * tableau de bord doit afficher quelque chose au premier chargement, sans paramètre.
     * La fenêtre retenue est renvoyée dans la réponse pour lever toute ambiguïté.
     */
    public static AnalyticsWindow window(Instant from, Instant to, String granularity) {
        return AnalyticsWindow.of(from, to, TimeGranularity.parse(granularity), Instant.now());
    }
}
