package com.yowyob.loyalty.shared.multitenancy;

import com.yowyob.loyalty.domain.shared.model.TenantId;
import com.yowyob.loyalty.infrastructure.kernelcore.adapter.KernelCoreTenantAdapter;
import com.yowyob.loyalty.infrastructure.redis.adapter.TenantCacheAdapter;
import com.yowyob.loyalty.shared.exception.TenantNotFoundException;
import com.yowyob.loyalty.shared.security.JwtClaimsExtractor;
import com.yowyob.loyalty.shared.util.RequestPaths;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;

import reactor.core.publisher.Mono;

@Component
@Order(-200)
@Profile("!dev")
public class TenantResolutionFilter implements WebFilter {

    private static final String[] PUBLIC_PATHS = {
            "/public/", "/actuator", "/swagger-ui", "/v3/api-docs", "/api-docs", "/webjars/"
    };
    private static final String ORGANIZATION_HEADER = "X-Organization-Id";

    private final TenantCacheAdapter tenantCacheAdapter;
    private final KernelCoreTenantAdapter kernelCoreTenantAdapter;
    private final JwtClaimsExtractor jwtClaimsExtractor;

    public TenantResolutionFilter(
            TenantCacheAdapter tenantCacheAdapter,
            KernelCoreTenantAdapter kernelCoreTenantAdapter,
            JwtClaimsExtractor jwtClaimsExtractor) {
        this.tenantCacheAdapter = tenantCacheAdapter;
        this.kernelCoreTenantAdapter = kernelCoreTenantAdapter;
        this.jwtClaimsExtractor = jwtClaimsExtractor;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String path = RequestPaths.withinApplication(exchange);
        for (String pub : PUBLIC_PATHS) {
            if (path.startsWith(pub)) return chain.filter(exchange);
        }

        String authHeader = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            // No JWT — let ApiKeyResolutionFilter handle it; Spring Security enforces auth at the end
            return chain.filter(exchange);
        }

        String rawToken = authHeader.substring(7);
        TenantId tenantId;
        try {
            tenantId = resolveTenantIdFromRequest(exchange, rawToken);
        } catch (Exception e) {
            return writeUnauthorized(exchange);
        }

        return resolveTenant(tenantId, rawToken, exchange, chain);
    }

    /**
     * L'organisation KernelCore cible (= "tenant" au sens de ce backend) est prioritairement
     * portée par le header X-Organization-Id, tel qu'exigé par le contrat KernelCore pour les
     * services métier scopés organisation — un login tenant-scopé "nu" ne porte pas de claim
     * d'organisation dans le JWT. Le claim JWT reste un repli pour un futur token issu du flux
     * discover/select-context (qui porterait alors un claim "oid").
     */
    private TenantId resolveTenantIdFromRequest(ServerWebExchange exchange, String rawToken) throws Exception {
        String orgHeader = exchange.getRequest().getHeaders().getFirst(ORGANIZATION_HEADER);
        if (orgHeader != null && !orgHeader.isBlank()) {
            return TenantId.of(java.util.UUID.fromString(orgHeader.trim()));
        }
        return jwtClaimsExtractor.extractTenantIdFromRawToken(rawToken);
    }

    private Mono<Void> resolveTenant(TenantId tenantId, String rawToken, ServerWebExchange exchange, WebFilterChain chain) {
        return tenantCacheAdapter.findById(tenantId)
                .switchIfEmpty(Mono.defer(() -> kernelCoreTenantAdapter.fetchAndCache(tenantId, rawToken)))
                .flatMap(tenant -> {
                    if (!tenant.isActive()) return writeUnauthorized(exchange);
                    TenantContext ctx = TenantContext.from(tenant);
                    return chain.filter(exchange).contextWrite(TenantContextHolder.withTenantContext(ctx));
                })
                .onErrorResume(TenantNotFoundException.class, e -> writeUnauthorized(exchange))
                .switchIfEmpty(Mono.defer(() -> writeUnauthorized(exchange)));
    }

    private Mono<Void> writeUnauthorized(ServerWebExchange exchange) {
        exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
        return exchange.getResponse().setComplete();
    }
}
