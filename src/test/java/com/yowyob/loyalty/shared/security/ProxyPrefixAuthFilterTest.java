package com.yowyob.loyalty.shared.security;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.adapter.ForwardedHeaderTransformer;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Non-régression : les gardes d'accès à secret statique doivent rester actives
 * derrière le reverse-proxy.
 *
 * <p>En production, Traefik strippe {@code /loyalty-api} et envoie {@code X-Forwarded-Prefix} ;
 * avec {@code server.forward-headers-strategy: framework}, le {@link ForwardedHeaderTransformer}
 * réinjecte ce préfixe dans l'URI de la requête. Un filtre qui compare
 * {@code getURI().getPath()} à {@code "/api/v1/..."} ne matche alors plus rien et laisse
 * passer la requête sans contrôle — c'est exactement ce qui avait ouvert
 * {@code /api/v1/admin/platform/**} et {@code /api/v1/payments/callbacks/**} sur Internet.
 *
 * <p>Chaque cas est donc joué deux fois : sans proxy (comme en local) et avec le préfixe
 * forwardé (comme en prod).
 */
class ProxyPrefixAuthFilterTest {

    private static final String SECRET = "s3cret-attendu";
    private static final String FORWARDED_PREFIX = "/loyalty-api";

    /** Chaîne qui note si elle a été appelée : si oui, le filtre a laissé passer la requête. */
    private static final class RecordingChain implements WebFilterChain {
        private final AtomicBoolean called = new AtomicBoolean(false);

        @Override
        public Mono<Void> filter(ServerWebExchange exchange) {
            called.set(true);
            return Mono.empty();
        }
    }

    /**
     * Construit l'exchange tel que l'application le voit réellement : la requête est passée
     * dans le vrai {@link ForwardedHeaderTransformer}, pas dans une simulation approximative.
     */
    private static ServerWebExchange exchange(String path, boolean behindProxy, String providedSecret, String secretHeader) {
        MockServerHttpRequest.BaseBuilder<?> builder = MockServerHttpRequest.get(path);
        if (behindProxy) {
            builder.header("X-Forwarded-Prefix", FORWARDED_PREFIX);
        }
        if (providedSecret != null) {
            builder.header(secretHeader, providedSecret);
        }
        MockServerWebExchange base = MockServerWebExchange.from((MockServerHttpRequest) builder.build());
        return base.mutate()
                .request(new ForwardedHeaderTransformer().apply(base.getRequest()))
                .build();
    }

    // ── Console plateforme ────────────────────────────────────────────────

    @Test
    void platformAdminRejetteUnSecretAbsentDerriereLeProxy() {
        PlatformAdminAuthFilter filter = new PlatformAdminAuthFilter(SECRET);
        RecordingChain chain = new RecordingChain();
        ServerWebExchange exchange = exchange("/api/v1/admin/platform/tenants", true, null, "X-Platform-Admin-Key");

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

        assertFalse(chain.called.get(), "la requête ne doit pas atteindre le contrôleur");
        assertEquals(HttpStatus.UNAUTHORIZED, exchange.getResponse().getStatusCode());
    }

    @Test
    void platformAdminRejetteUnMauvaisSecretDerriereLeProxy() {
        PlatformAdminAuthFilter filter = new PlatformAdminAuthFilter(SECRET);
        RecordingChain chain = new RecordingChain();
        ServerWebExchange exchange = exchange("/api/v1/admin/platform/tenants", true, "mauvais-secret", "X-Platform-Admin-Key");

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

        assertFalse(chain.called.get());
        assertEquals(HttpStatus.UNAUTHORIZED, exchange.getResponse().getStatusCode());
    }

    @Test
    void platformAdminAccepteLeBonSecretAvecEtSansProxy() {
        for (boolean behindProxy : new boolean[] {false, true}) {
            PlatformAdminAuthFilter filter = new PlatformAdminAuthFilter(SECRET);
            RecordingChain chain = new RecordingChain();
            ServerWebExchange exchange = exchange("/api/v1/admin/platform/tenants", behindProxy, SECRET, "X-Platform-Admin-Key");

            StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

            assertTrue(chain.called.get(), "behindProxy=" + behindProxy);
        }
    }

    @Test
    void platformAdminFermeLaRouteQuandAucunSecretNEstConfigure() {
        PlatformAdminAuthFilter filter = new PlatformAdminAuthFilter("");
        RecordingChain chain = new RecordingChain();
        ServerWebExchange exchange = exchange("/api/v1/admin/platform/tenants", true, "n-importe-quoi", "X-Platform-Admin-Key");

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

        assertFalse(chain.called.get());
        assertEquals(HttpStatus.UNAUTHORIZED, exchange.getResponse().getStatusCode());
    }

    @Test
    void platformAdminLaissePasserLesAutresRoutes() {
        PlatformAdminAuthFilter filter = new PlatformAdminAuthFilter(SECRET);
        RecordingChain chain = new RecordingChain();
        ServerWebExchange exchange = exchange("/api/v1/members", true, null, "X-Platform-Admin-Key");

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

        assertTrue(chain.called.get(), "les routes hors console plateforme ne concernent pas ce filtre");
    }

    // ── Callbacks de paiement ─────────────────────────────────────────────

    @Test
    void paymentCallbackRejetteUnSecretAbsentDerriereLeProxy() {
        PaymentCallbackAuthFilter filter = new PaymentCallbackAuthFilter(SECRET);
        RecordingChain chain = new RecordingChain();
        ServerWebExchange exchange = exchange("/api/v1/payments/callbacks/kernel-core", true, null, "X-Payment-Callback-Key");

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

        assertFalse(chain.called.get(), "un callback non authentifié ne doit pas être rejouable");
        assertEquals(HttpStatus.UNAUTHORIZED, exchange.getResponse().getStatusCode());
    }

    @Test
    void paymentCallbackAccepteLeBonSecretAvecEtSansProxy() {
        for (boolean behindProxy : new boolean[] {false, true}) {
            PaymentCallbackAuthFilter filter = new PaymentCallbackAuthFilter(SECRET);
            RecordingChain chain = new RecordingChain();
            ServerWebExchange exchange = exchange("/api/v1/payments/callbacks/kernel-core", behindProxy, SECRET, "X-Payment-Callback-Key");

            StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

            assertTrue(chain.called.get(), "behindProxy=" + behindProxy);
        }
    }
}
