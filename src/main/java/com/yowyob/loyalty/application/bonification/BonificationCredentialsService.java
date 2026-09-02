package com.yowyob.loyalty.application.bonification;

import com.yowyob.loyalty.domain.shared.model.AuditInfo;
import com.yowyob.loyalty.domain.tenant.model.Tenant;
import com.yowyob.loyalty.domain.tenant.model.TenantConfig;
import com.yowyob.loyalty.domain.tenant.port.out.TenantRepository;
import com.yowyob.loyalty.infrastructure.security.crypto.SecretCipher;
import com.yowyob.loyalty.shared.multitenancy.TenantContext;
import com.yowyob.loyalty.shared.multitenancy.TenantContextHolder;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.Locale;

/**
 * Enregistrement des identifiants Bonification propres au tenant courant.
 *
 * <p>Ils n'étaient jusqu'ici modifiables qu'en écrivant directement dans le JSONB
 * {@code tenants.config} : aucun endpoint ne les exposait, et la valeur y était en clair.
 * Le chiffrement est appliqué à la frontière de persistance (TenantMapper) ; ce service
 * refuse simplement d'écrire tant qu'aucune clé n'est configurée, plutôt que de reconduire
 * un stockage en clair.
 */
@Service
public class BonificationCredentialsService {

    private final TenantRepository tenantRepository;
    private final SecretCipher secretCipher;

    public BonificationCredentialsService(TenantRepository tenantRepository, SecretCipher secretCipher) {
        this.tenantRepository = tenantRepository;
        this.secretCipher = secretCipher;
    }

    /** @return l'identifiant enregistré ; le mot de passe n'est jamais renvoyé. */
    public Mono<String> update(String username, String password) {
        if (!secretCipher.isEnabled()) {
            return Mono.error(new IllegalStateException(
                    "Chiffrement des secrets non configuré (app.security.credential-encryption-key) : "
                            + "l'enregistrement est refusé pour ne pas stocker le mot de passe en clair"));
        }
        return applyConfig(config -> config.withBonificationCredentials(username, password))
                .thenReturn(username);
    }

    /** Efface les identifiants du tenant : il retombe sur ceux du déploiement. */
    public Mono<Void> clear() {
        return applyConfig(config -> config.withBonificationCredentials(null, null)).then();
    }

    public Mono<Boolean> isConfigured() {
        return TenantContextHolder.getTenantId()
                .flatMap(tenantRepository::findById)
                .map(tenant -> {
                    TenantConfig config = tenant.getConfig();
                    return config != null && config.bonificationApiUsername() != null
                            && !config.bonificationApiUsername().isBlank();
                })
                .defaultIfEmpty(false);
    }

    private Mono<Tenant> applyConfig(java.util.function.UnaryOperator<TenantConfig> change) {
        return TenantContextHolder.getTenantContext()
                .flatMap(context -> tenantRepository.findById(context.tenantId())
                        // Les tenants viennent de Kernel Core : la ligne locale n'existe que si
                        // un réglage propre à la fidélité a déjà été enregistré. On la crée à la
                        // première configuration plutôt que d'exiger un provisionnement préalable.
                        .switchIfEmpty(Mono.fromSupplier(() -> projectionOf(context)))
                        .map(tenant -> tenant.withConfig(
                                change.apply(tenant.getConfig() != null ? tenant.getConfig() : TenantConfig.defaults())))
                        .flatMap(tenantRepository::save));
    }

    /** Projection locale d'une organisation Kernel Core, porteuse des seuls réglages fidélité. */
    private static Tenant projectionOf(TenantContext context) {
        String name = context.tenantName() != null && !context.tenantName().isBlank()
                ? context.tenantName()
                : context.tenantId().value().toString();
        return new Tenant(
                context.tenantId(),
                name,
                slugify(name),
                context.tenantStatus(),
                context.tenantPlan(),
                TenantConfig.defaults(),
                AuditInfo.now("system"));
    }

    private static String slugify(String name) {
        String slug = name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-+|-+$", "");
        return slug.isBlank() ? "tenant" : slug;
    }
}
