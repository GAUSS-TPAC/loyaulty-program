package com.yowyob.loyalty.shared.multitenancy;

import com.yowyob.loyalty.domain.shared.model.TenantId;
import com.yowyob.loyalty.domain.tenant.model.enums.TenantPlan;
import com.yowyob.loyalty.domain.tenant.model.enums.TenantStatus;
import com.yowyob.loyalty.shared.security.JwtClaimsExtractor;
import com.yowyob.loyalty.shared.util.RequestPaths;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;

import reactor.core.publisher.Mono;
import java.util.UUID;

@Component
@Order(-200)
@Profile("dev")
public class DevTenantResolutionFilter implements WebFilter {

    private final JwtClaimsExtractor jwtClaimsExtractor;
    private final TenantId defaultTenantId;

    public DevTenantResolutionFilter(
            JwtClaimsExtractor jwtClaimsExtractor,
            @Value("${app.dev.default-tenant-id:00000000-0000-0000-0000-000000000001}") String defaultTenantId
    ) {
        this.jwtClaimsExtractor = jwtClaimsExtractor;
        this.defaultTenantId = TenantId.of(UUID.fromString(defaultTenantId));
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        if (isPublicPath(RequestPaths.withinApplication(exchange))) {
            return chain.filter(exchange);
        }

        TenantContext ctx = resolveTenantContext(exchange);
        return chain.filter(exchange)
                .contextWrite(TenantContextHolder.withTenantContext(ctx));
    }

    private TenantContext resolveTenantContext(ServerWebExchange exchange) {
        String authHeader = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            try {
                TenantId tenantId = jwtClaimsExtractor.extractTenantIdFromRawToken(authHeader.substring(7));
                return new TenantContext(tenantId, "JWT Tenant", TenantStatus.ACTIVE, TenantPlan.PRO);
            } catch (Exception ignored) {
                // fallback tenant dev
            }
        }
        return new TenantContext(defaultTenantId, "Dev Tenant", TenantStatus.ACTIVE, TenantPlan.PRO);
    }

    private boolean isPublicPath(String path) {
        return path.startsWith("/public/")
                || path.startsWith("/actuator")
                || path.startsWith("/swagger-ui")
                || path.startsWith("/v3/api-docs")
                || path.startsWith("/api-docs")
                || path.startsWith("/webjars/");
    }
}
