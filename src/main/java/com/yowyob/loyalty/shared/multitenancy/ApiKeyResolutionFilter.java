package com.yowyob.loyalty.shared.multitenancy;

import com.yowyob.loyalty.application.tenant.ApiKeyService;
import com.yowyob.loyalty.domain.tenant.model.ApiKey;
import com.yowyob.loyalty.infrastructure.kernelcore.adapter.KernelCoreTenantAdapter;
import com.yowyob.loyalty.infrastructure.redis.adapter.TenantCacheAdapter;
import com.yowyob.loyalty.shared.util.RequestPaths;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;

import reactor.core.publisher.Mono;
import java.util.List;

/**
 * Filtre d'authentification par clé API (X-Api-Key header).
 * Priorité plus basse que TenantResolutionFilter : si un Bearer JWT est présent,
 * TenantResolutionFilter gère la requête en premier.
 * Ce filtre s'active uniquement quand Authorization est absent et X-Api-Key est présent.
 *
 * Une clé API valide représente un accès tenant-scopé de niveau administrateur (le
 * portail développeur s'y connecte pour gérer ses propres clés/webhooks) : ce filtre
 * lui accorde donc l'autorité ROLE_TENANT_ADMIN, sans quoi tout @PreAuthorize("hasRole(...)")
 * échouerait pour une requête authentifiée uniquement par clé API.
 */
@Component
@Order(-199)
@Profile("!dev")
public class ApiKeyResolutionFilter implements WebFilter {

    private static final String API_KEY_HEADER = "X-Api-Key";
    private static final String[] PUBLIC_PATHS = {
            "/public/", "/actuator", "/swagger-ui", "/v3/api-docs", "/api-docs", "/webjars/"
    };

    private final ApiKeyService apiKeyService;
    private final TenantCacheAdapter tenantCache;
    private final KernelCoreTenantAdapter kernelAdapter;

    public ApiKeyResolutionFilter(ApiKeyService apiKeyService,
                                  TenantCacheAdapter tenantCache,
                                  KernelCoreTenantAdapter kernelAdapter) {
        this.apiKeyService = apiKeyService;
        this.tenantCache = tenantCache;
        this.kernelAdapter = kernelAdapter;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String path = RequestPaths.withinApplication(exchange);
        for (String pub : PUBLIC_PATHS) {
            if (path.startsWith(pub)) return chain.filter(exchange);
        }

        String authHeader = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            // JWT présent : TenantResolutionFilter prend la main
            return chain.filter(exchange);
        }

        String rawKey = exchange.getRequest().getHeaders().getFirst(API_KEY_HEADER);
        if (rawKey == null || rawKey.isBlank()) {
            // Ni JWT ni API key : TenantResolutionFilter retournera 401
            return chain.filter(exchange);
        }

        return apiKeyService.validate(rawKey)
                .flatMap(apiKey -> resolveAndSetTenantContext(apiKey, exchange, chain))
                .switchIfEmpty(Mono.defer(() -> writeUnauthorized(exchange)))
                .onErrorResume(e -> writeUnauthorized(exchange));
    }

    private Mono<Void> resolveAndSetTenantContext(ApiKey apiKey, ServerWebExchange exchange, WebFilterChain chain) {
        return tenantCache.findById(apiKey.tenantId())
                .switchIfEmpty(Mono.defer(() -> kernelAdapter.fetchAndCache(apiKey.tenantId())))
                .flatMap(tenant -> {
                    if (!tenant.isActive()) return writeUnauthorized(exchange);
                    TenantContext ctx = TenantContext.from(tenant);
                    Authentication authentication = new UsernamePasswordAuthenticationToken(
                            "api-key:" + apiKey.id(), null, List.of(new SimpleGrantedAuthority("ROLE_TENANT_ADMIN")));
                    return chain.filter(exchange)
                            .contextWrite(TenantContextHolder.withTenantContext(ctx))
                            .contextWrite(ReactiveSecurityContextHolder.withSecurityContext(
                                    Mono.just(new SecurityContextImpl(authentication))));
                });
    }

    private Mono<Void> writeUnauthorized(ServerWebExchange exchange) {
        exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
        return exchange.getResponse().setComplete();
    }
}
