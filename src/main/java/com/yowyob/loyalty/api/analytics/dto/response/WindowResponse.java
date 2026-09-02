package com.yowyob.loyalty.api.analytics.dto.response;

import com.yowyob.loyalty.domain.analytics.model.AnalyticsWindow;

import java.time.Instant;

/**
 * Fenêtre effectivement appliquée.
 *
 * <p>Toujours renvoyée avec les données : les bornes sont facultatives à l'appel et
 * complétées par défaut côté serveur. Sans ce rappel, un client qui n'a rien passé ne
 * saurait pas sur quelle période porte la courbe qu'il affiche.
 */
public record WindowResponse(Instant from, Instant to, String granularity) {

    public static WindowResponse from(AnalyticsWindow window) {
        return new WindowResponse(window.from(), window.to(), window.granularity().name());
    }
}
