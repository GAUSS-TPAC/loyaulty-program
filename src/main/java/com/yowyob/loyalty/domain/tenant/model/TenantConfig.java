package com.yowyob.loyalty.domain.tenant.model;

import com.yowyob.loyalty.domain.shared.exception.DomainValidationException;
import java.util.Collections;
import java.util.List;

public record TenantConfig(
    String defaultCurrencyCode,
    boolean walletAutoActivate,
    Integer pointExpiryDays,
    List<String> notificationChannels,
    String bonificationApiUsername,
    String bonificationApiPassword
) {
    public TenantConfig {
        if (defaultCurrencyCode == null || defaultCurrencyCode.isBlank()) {
            throw new DomainValidationException("defaultCurrencyCode ne doit pas être null");
        }
        notificationChannels = notificationChannels != null ? List.copyOf(notificationChannels) : Collections.emptyList();
    }

    /**
     * Identifiants de l'API Bonification propres au tenant. Passer {@code null} aux deux
     * paramètres efface la configuration et fait retomber le tenant sur les identifiants
     * globaux du déploiement (BONIFICATION_LOGIN/PASSWORD).
     */
    public TenantConfig withBonificationCredentials(String username, String password) {
        return new TenantConfig(defaultCurrencyCode, walletAutoActivate, pointExpiryDays,
                notificationChannels, username, password);
    }

    public static TenantConfig defaults() {
        return new TenantConfig(
            "XAF",
            false,
            365,
            Collections.emptyList(),
            null,
            null
        );
    }
}
