package com.yowyob.loyalty.infrastructure.security.config;

import com.yowyob.loyalty.infrastructure.security.JwtAuthenticationFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.method.configuration.EnableReactiveMethodSecurity;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.SecurityWebFiltersOrder;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;

@Configuration
@EnableWebFluxSecurity
@EnableReactiveMethodSecurity
@Profile("!dev")
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final com.yowyob.loyalty.infrastructure.security.SecurityContextRepository securityContextRepository;

    public SecurityConfig(
            JwtAuthenticationFilter jwtAuthenticationFilter,
            com.yowyob.loyalty.infrastructure.security.SecurityContextRepository securityContextRepository) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
        this.securityContextRepository = securityContextRepository;
    }

    @Bean
    public SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http) {
        return http
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .httpBasic(ServerHttpSecurity.HttpBasicSpec::disable)
                .formLogin(ServerHttpSecurity.FormLoginSpec::disable)
                // Sans entry point explicite, l'ExceptionTranslationWebFilter réactif retombe
                // sur HttpBasicServerAuthenticationEntryPoint (WWW-Authenticate: Basic), ce qui
                // fait surgir la boîte de dialogue de connexion native du navigateur sur tout
                // 401 — même avec httpBasic désactivé ci-dessus.
                .exceptionHandling(spec -> spec.authenticationEntryPoint((exchange, ex) -> {
                    exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
                    exchange.getResponse().getHeaders().set(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
                    return exchange.getResponse().setComplete();
                }))
                // Sans ceci, notre SecurityContextRepository (qui valide le Bearer JWT et en
                // extrait les autorités ROLE_*) n'est jamais invoqué pour peupler le contexte
                // réactif : JwtAuthenticationFilter (AuthenticationWebFilter) n'aide pas non plus
                // ici, son ServerAuthenticationConverter par défaut ne reconnaît que Basic Auth,
                // pas Bearer. Résultat vérifié en conditions réelles : toute requête authentifiée
                // par un vrai JWT KernelCore était traitée comme anonyme -> 401 systématique sur
                // anyExchange().authenticated(), y compris pour un admin légitime.
                .securityContextRepository(securityContextRepository)
                .authorizeExchange(exchanges -> exchanges
                        .pathMatchers(
                                "/public/**",
                                "/actuator/health",
                                "/swagger-ui.html",
                                "/swagger-ui/**",
                                "/api-docs/**",
                                "/v3/api-docs/**",
                                "/webjars/**",
                                "/api/v1/auth/login",
                                "/api/v1/auth/login/mfa",
                                "/api/v1/auth/register",
                                // Non rattaché à un tenant : protégé par PlatformAdminAuthFilter
                                // (secret statique X-Platform-Admin-Key), pas par JWT/clé API.
                                "/api/v1/admin/platform/**",
                                // Callbacks de la passerelle de paiement : appelés par Kernel Core,
                                // donc sans JWT. Protégés par PaymentCallbackAuthFilter
                                // (secret statique X-Payment-Callback-Key).
                                "/api/v1/payments/callbacks/**"
                        ).permitAll()
                        .anyExchange().authenticated()
                )
                .addFilterAt(jwtAuthenticationFilter, SecurityWebFiltersOrder.AUTHENTICATION)
                .build();
    }
}
