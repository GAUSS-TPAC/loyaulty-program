package com.yowyob.loyalty.shared.security;

import com.yowyob.loyalty.shared.util.RequestPaths;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;

import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * Garde d'accès des callbacks de la passerelle de paiement
 * (/api/v1/payments/callbacks/**), qui arrivent sans JWT ni clé d'API tenant.
 *
 * <p>Le secret est le même que celui transmis à Kernel Core avec la {@code callbackUrl}.
 * Tant qu'aucun secret n'est configuré, la route est fermée : un callback non authentifié
 * pourrait sinon être rejoué à volonté pour forcer des réconciliations.
 *
 * <p>C'est une défense de surface, pas la garantie d'intégrité : celle-ci vient de ce que la
 * confirmation ré-interroge toujours la passerelle plutôt que de croire le corps reçu.
 */
@Component
@Order(-197)
public class PaymentCallbackAuthFilter implements WebFilter {

    private static final String CALLBACK_PATH_PREFIX = "/api/v1/payments/callbacks";
    private static final String SECRET_HEADER = "X-Payment-Callback-Key";

    private final String configuredSecret;

    public PaymentCallbackAuthFilter(
            @Value("${app.kernel-core.payments.callback-secret:}") String configuredSecret) {
        this.configuredSecret = configuredSecret;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String path = RequestPaths.withinApplication(exchange);
        if (!path.startsWith(CALLBACK_PATH_PREFIX)) {
            return chain.filter(exchange);
        }

        String provided = exchange.getRequest().getHeaders().getFirst(SECRET_HEADER);
        if (configuredSecret.isBlank() || provided == null || !secretMatches(provided)) {
            exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
            return exchange.getResponse().setComplete();
        }

        return chain.filter(exchange);
    }

    /**
     * Comparaison à temps constant : un {@code String.equals} sort à la première
     * différence, ce qui laisse fuir la longueur du préfixe correct octet par octet.
     */
    private boolean secretMatches(String provided) {
        return MessageDigest.isEqual(
                provided.getBytes(StandardCharsets.UTF_8),
                configuredSecret.getBytes(StandardCharsets.UTF_8));
    }
}
