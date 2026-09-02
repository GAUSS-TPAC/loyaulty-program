package com.yowyob.loyalty.infrastructure.kernelcore.config;

import com.yowyob.loyalty.domain.wallet.port.out.PaymentGatewayPort;
import com.yowyob.loyalty.infrastructure.kernelcore.adapter.KernelCoreActorAdapter;
import com.yowyob.loyalty.infrastructure.kernelcore.adapter.KernelCoreAuthAdapter;
import com.yowyob.loyalty.infrastructure.kernelcore.adapter.KernelCoreDeveloperInviteAdapter;
import com.yowyob.loyalty.infrastructure.kernelcore.adapter.KernelCorePaymentGatewayAdapter;
import com.yowyob.loyalty.infrastructure.kernelcore.adapter.KernelCoreTenantAdapter;
import com.yowyob.loyalty.infrastructure.kernelcore.adapter.KernelCoreTokenService;
import com.yowyob.loyalty.infrastructure.redis.adapter.TenantCacheAdapter;
import io.netty.channel.ChannelOption;
import reactor.netty.resources.ConnectionProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;

/**
 * Kernel Core exige X-Client-Id + X-Api-Key sur tous les appels (server-to-server),
 * le Bearer JWT s'ajoutant par-dessus pour les endpoints liés à l'utilisateur courant (ex: /api/actors/me).
 * KernelCoreProperties est déjà auto-enregistrée via @Component + @ConfigurationProperties,
 * inutile (et générateur de doublon de bean) de la redéclarer avec @EnableConfigurationProperties ici.
 */
@Configuration
public class KernelCoreConfig {

    @Bean
    public WebClient kernelCoreWebClient(KernelCoreProperties properties) {
        String baseUrl = properties.getBaseUrl();
        if (baseUrl.endsWith("/")) baseUrl = baseUrl.substring(0, baseUrl.length() - 1);

        // Kernel Core ferme ses connexions inactives sans que le pool client le sache : la
        // première requête après une période creuse repartait sur une socket morte et
        // échouait en « Connexion réinitialisée par le correspondant », que l'utilisateur
        // lisait « KernelCore indisponible ». Constaté le 2026-09-02 sur le login, après
        // ~20 s d'inactivité. On expire donc les connexions côté client avant lui, et on
        // les évince en tâche de fond plutôt qu'au moment d'en avoir besoin.
        ConnectionProvider connectionProvider = ConnectionProvider.builder("kernel-core")
                .maxIdleTime(Duration.ofSeconds(15))
                .maxLifeTime(Duration.ofMinutes(5))
                .evictInBackground(Duration.ofSeconds(30))
                .build();

        HttpClient httpClient = HttpClient.create(connectionProvider)
                .responseTimeout(Duration.ofMillis(properties.getReadTimeoutMs()))
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, properties.getConnectTimeoutMs());

        return WebClient.builder()
                .baseUrl(baseUrl)
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .defaultHeader("X-Client-Id", properties.getServiceClientId())
                .defaultHeader("X-Api-Key", properties.getServiceClientSecret())
                .build();
    }

    @Bean
    public KernelCoreTokenService kernelCoreTokenService(KernelCoreProperties properties) {
        return new KernelCoreTokenService(properties);
    }

    @Bean
    public KernelCoreTenantAdapter kernelCoreTenantAdapter(
            @Qualifier("kernelCoreWebClient") WebClient kernelCoreWebClient,
            KernelCoreTokenService kernelCoreTokenService,
            TenantCacheAdapter tenantCacheAdapter) {
        return new KernelCoreTenantAdapter(kernelCoreWebClient, kernelCoreTokenService, tenantCacheAdapter);
    }

    @Bean
    public KernelCoreActorAdapter kernelCoreActorAdapter(
            @Qualifier("kernelCoreWebClient") WebClient kernelCoreWebClient) {
        return new KernelCoreActorAdapter(kernelCoreWebClient);
    }

    @Bean
    public KernelCoreAuthAdapter kernelCoreAuthAdapter(
            @Qualifier("kernelCoreWebClient") WebClient kernelCoreWebClient) {
        return new KernelCoreAuthAdapter(kernelCoreWebClient);
    }

    @Bean
    public KernelCoreDeveloperInviteAdapter kernelCoreDeveloperInviteAdapter(
            @Qualifier("kernelCoreWebClient") WebClient kernelCoreWebClient) {
        return new KernelCoreDeveloperInviteAdapter(kernelCoreWebClient);
    }

    /**
     * Passerelle de paiement réelle. Désactivée par défaut : sans credentials Kernel Core
     * valides, mieux vaut le stub explicite (PaymentGatewayStub) qu'un 502 à chaque recharge.
     */
    @Bean
    @ConditionalOnProperty(prefix = "app.kernel-core.payments", name = "enabled", havingValue = "true")
    public PaymentGatewayPort kernelCorePaymentGatewayAdapter(
            @Qualifier("kernelCoreWebClient") WebClient kernelCoreWebClient,
            KernelCoreTokenService kernelCoreTokenService,
            KernelCoreProperties properties) {
        return new KernelCorePaymentGatewayAdapter(kernelCoreWebClient, kernelCoreTokenService, properties);
    }
}
