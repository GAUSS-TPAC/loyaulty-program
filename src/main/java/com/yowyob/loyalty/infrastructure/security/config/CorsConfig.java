package com.yowyob.loyalty.infrastructure.security.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsWebFilter;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
public class CorsConfig {

    /**
     * Origines autorisées, pilotées par configuration plutôt qu'en dur : la liste
     * combinée à {@code allowCredentials(true)} autorise des requêtes authentifiées
     * (cookies, Authorization, X-Api-Key), donc y laisser {@code http://localhost}
     * en production revient à ouvrir l'API à n'importe quelle page servie en local
     * sur le poste d'une victime. Les origines de dev sont ajoutées uniquement par
     * {@code application-dev.yml}.
     */
    private final List<String> allowedOrigins;

    public CorsConfig(@Value("${app.cors.allowed-origins}") List<String> allowedOrigins) {
        this.allowedOrigins = allowedOrigins;
    }

    @Bean
    public CorsWebFilter corsWebFilter() {
        CorsConfiguration corsConfig = new CorsConfiguration();
        corsConfig.setAllowedOrigins(allowedOrigins);
        corsConfig.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH"));
        corsConfig.setAllowedHeaders(List.of(
                "Authorization", "Content-Type", "X-Api-Key",
                "Idempotency-Key", "X-Request-Id", "Accept",
                "X-Organization-Id"));
        corsConfig.setAllowCredentials(true);
        corsConfig.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", corsConfig);

        return new CorsWebFilter(source);
    }
}
