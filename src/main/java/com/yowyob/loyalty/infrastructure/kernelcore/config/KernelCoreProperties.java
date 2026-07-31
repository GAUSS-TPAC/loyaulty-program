package com.yowyob.loyalty.infrastructure.kernelcore.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app.kernel-core")
public class KernelCoreProperties {

    private String baseUrl = "http://localhost:8090";
    private String serviceClientId;
    private String serviceClientSecret;
    private String tokenEndpoint;
    private int connectTimeoutMs = 3000;
    private int readTimeoutMs = 5000;
    /** Tenant plateforme KernelCore (X-Tenant-Id) sous lequel les admins de ce déploiement se connectent. */
    private String tenantId;
    /** Organisation KernelCore cible pour l'inscription publique (POST /api/auth/discover-sign-up-contexts). */
    private String organizationCode;
    private Payments payments = new Payments();

    public String resolvedTokenEndpoint() {
        if (tokenEndpoint != null && !tokenEndpoint.isBlank()) {
            return tokenEndpoint;
        }
        String base = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        return base + "/oauth2/token";
    }

    public boolean hasTokenEndpoint() {
        return tokenEndpoint != null && !tokenEndpoint.isBlank();
    }

    public String getBaseUrl() { return baseUrl; }
    public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }

    public String getServiceClientId() { return serviceClientId; }
    public void setServiceClientId(String serviceClientId) { this.serviceClientId = serviceClientId; }

    public String getServiceClientSecret() { return serviceClientSecret; }
    public void setServiceClientSecret(String serviceClientSecret) { this.serviceClientSecret = serviceClientSecret; }

    public String getTokenEndpoint() { return tokenEndpoint; }
    public void setTokenEndpoint(String tokenEndpoint) { this.tokenEndpoint = tokenEndpoint; }

    public int getConnectTimeoutMs() { return connectTimeoutMs; }
    public void setConnectTimeoutMs(int connectTimeoutMs) { this.connectTimeoutMs = connectTimeoutMs; }

    public int getReadTimeoutMs() { return readTimeoutMs; }
    public void setReadTimeoutMs(int readTimeoutMs) { this.readTimeoutMs = readTimeoutMs; }

    public String getTenantId() { return tenantId; }
    public void setTenantId(String tenantId) { this.tenantId = tenantId; }

    public String getOrganizationCode() { return organizationCode; }
    public void setOrganizationCode(String organizationCode) { this.organizationCode = organizationCode; }

    public Payments getPayments() { return payments; }
    public void setPayments(Payments payments) { this.payments = payments; }

    /** Passerelle de paiement Kernel Core (payment-gateway-controller, /api/payments/orders). */
    public static class Payments {

        /** Sans ceci, les recharges passent par le stub en mémoire : aucun encaissement réel. */
        private boolean enabled = false;

        /**
         * PlatformServiceCode facturé pour l'encaissement. Le catalogue Kernel Core ne
         * contient pas de code LOYALTY : on retombe sur ORGANIZATION, toujours effectif pour
         * toute organisation même sans abonnement explicite.
         */
        private String serviceCode = "ORGANIZATION";

        /** URL publique que Kernel Core rappelle en fin de parcours de paiement. */
        private String callbackUrl;

        /** Secret attendu dans l'en-tête X-Payment-Callback-Key des callbacks entrants. */
        private String callbackSecret;

        private String defaultProvider = "MYCOOLPAY";

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }

        public String getServiceCode() { return serviceCode; }
        public void setServiceCode(String serviceCode) { this.serviceCode = serviceCode; }

        public String getCallbackUrl() { return callbackUrl; }
        public void setCallbackUrl(String callbackUrl) { this.callbackUrl = callbackUrl; }

        public String getCallbackSecret() { return callbackSecret; }
        public void setCallbackSecret(String callbackSecret) { this.callbackSecret = callbackSecret; }

        public String getDefaultProvider() { return defaultProvider; }
        public void setDefaultProvider(String defaultProvider) { this.defaultProvider = defaultProvider; }
    }
}
