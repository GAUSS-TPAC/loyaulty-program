package com.yowyob.loyalty.shared.util;

import org.springframework.web.server.ServerWebExchange;

/**
 * Extraction du chemin de requête pour les {@code WebFilter} qui décident d'appliquer
 * ou non un contrôle en fonction d'un préfixe de route.
 *
 * <p><strong>Ne jamais utiliser {@code exchange.getRequest().getURI().getPath()} pour ça.</strong>
 * Derrière le reverse-proxy, Traefik strippe {@code /loyalty-api} et envoie
 * {@code X-Forwarded-Prefix}. Avec {@code server.forward-headers-strategy: framework},
 * le {@code ForwardedHeaderTransformer} réinjecte ce préfixe dans l'URI
 * ({@code /loyalty-api/api/v1/...}) et le pose comme {@code contextPath}. Un
 * {@code startsWith("/api/v1/...")} devient alors systématiquement faux en production
 * alors qu'il est vrai en local — un filtre d'authentification écrit ainsi ne s'exécute
 * jamais une fois déployé, et laisse passer la requête sans contrôle.
 *
 * <p>{@code pathWithinApplication()} retire le contextPath et redonne le chemin applicatif
 * ({@code /api/v1/...}) dans les deux environnements. C'est aussi ce que Spring Security
 * utilise pour ses {@code pathMatchers}, donc les deux couches restent cohérentes.
 */
public final class RequestPaths {

    private RequestPaths() {
    }

    public static String withinApplication(ServerWebExchange exchange) {
        return exchange.getRequest().getPath().pathWithinApplication().value();
    }
}
