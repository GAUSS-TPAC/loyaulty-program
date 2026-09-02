package com.yowyob.loyalty.application.auth;

import com.yowyob.loyalty.infrastructure.kernelcore.adapter.KernelCoreActorAdapter;
import com.yowyob.loyalty.infrastructure.kernelcore.adapter.KernelCoreAuthAdapter;
import com.yowyob.loyalty.infrastructure.kernelcore.adapter.KernelCoreTenantAdapter;
import com.yowyob.loyalty.infrastructure.kernelcore.config.KernelCoreProperties;
import com.yowyob.loyalty.infrastructure.kernelcore.dto.KernelForgotPasswordResponseDto;
import com.yowyob.loyalty.infrastructure.kernelcore.dto.KernelIssuedAuthChallengeDto;
import com.yowyob.loyalty.infrastructure.kernelcore.dto.KernelPasswordResetContextDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Le parcours « mot de passe oublié » enchaîne deux appels KernelCore et touche à deux
 * propriétés qu'un refactor casse facilement : ne jamais révéler si un compte existe, et
 * réinitialiser le compte du tenant de ce déploiement quand la même adresse existe ailleurs.
 */
public class AuthServiceCredentialsTest {

    private static final String PLATFORM_TENANT = "11111111-1111-1111-1111-111111111111";

    private KernelCoreAuthAdapter kernelCoreAuthAdapter;
    private KernelCoreProperties properties;
    private AuthService authService;

    private static KernelPasswordResetContextDto context(String contextId, String tenantId) {
        KernelPasswordResetContextDto dto = new KernelPasswordResetContextDto();
        dto.setContextId(contextId);
        dto.setTenantId(tenantId);
        return dto;
    }

    private static KernelForgotPasswordResponseDto discovered(String selectionToken,
                                                              List<KernelPasswordResetContextDto> contexts) {
        KernelForgotPasswordResponseDto dto = new KernelForgotPasswordResponseDto();
        dto.setSelectionToken(selectionToken);
        dto.setContexts(contexts);
        dto.setMatchingAccountCount(contexts.size());
        return dto;
    }

    private static KernelIssuedAuthChallengeDto challenge(String deliveryMode) {
        KernelIssuedAuthChallengeDto dto = new KernelIssuedAuthChallengeDto();
        dto.setDeliveryMode(deliveryMode);
        dto.setExpiresInSeconds(900);
        return dto;
    }

    @BeforeEach
    void setup() {
        kernelCoreAuthAdapter = Mockito.mock(KernelCoreAuthAdapter.class);
        properties = new KernelCoreProperties();
        properties.setTenantId(PLATFORM_TENANT);
        authService = new AuthService(kernelCoreAuthAdapter, properties,
                Mockito.mock(KernelCoreActorAdapter.class), Mockito.mock(KernelCoreTenantAdapter.class));
    }

    @Test
    void issuesTheResetChallengeForTheDiscoveredAccount() {
        when(kernelCoreAuthAdapter.forgotPassword("admin@x.com"))
                .thenReturn(Mono.just(discovered("sel-1", List.of(context("ctx-1", PLATFORM_TENANT)))));
        when(kernelCoreAuthAdapter.issuePasswordReset("sel-1", "ctx-1"))
                .thenReturn(Mono.just(challenge("SMTP")));

        StepVerifier.create(authService.forgotPassword("admin@x.com"))
                .assertNext(result -> assertEquals("SMTP", result.deliveryMode()))
                .verifyComplete();
    }

    /**
     * KernelCore répond en succès avec zéro contexte pour une adresse inconnue : traiter ce cas
     * comme une erreur transformerait le formulaire en oracle d'énumération d'adresses.
     */
    @Test
    void staysSilentWhenNoAccountMatches() {
        when(kernelCoreAuthAdapter.forgotPassword("inconnu@x.com"))
                .thenReturn(Mono.just(discovered(null, List.of())));

        StepVerifier.create(authService.forgotPassword("inconnu@x.com"))
                .assertNext(result -> assertNull(result.deliveryMode()))
                .verifyComplete();

        verify(kernelCoreAuthAdapter, never()).issuePasswordReset(anyString(), anyString());
    }

    /** Même adresse dans plusieurs tenants : c'est le compte du tenant configuré qu'on réinitialise. */
    @Test
    void resetsTheAccountOfTheConfiguredPlatformTenant() {
        when(kernelCoreAuthAdapter.forgotPassword("admin@x.com"))
                .thenReturn(Mono.just(discovered("sel-1", List.of(
                        context("ctx-autre", "99999999-9999-9999-9999-999999999999"),
                        context("ctx-plateforme", PLATFORM_TENANT)))));
        when(kernelCoreAuthAdapter.issuePasswordReset(anyString(), anyString()))
                .thenReturn(Mono.just(challenge("SMTP")));

        StepVerifier.create(authService.forgotPassword("admin@x.com")).expectNextCount(1).verifyComplete();

        ArgumentCaptor<String> contextId = ArgumentCaptor.forClass(String.class);
        verify(kernelCoreAuthAdapter).issuePasswordReset(Mockito.eq("sel-1"), contextId.capture());
        assertEquals("ctx-plateforme", contextId.getValue());
    }

    /** Sans tenant configuré, la découverte fait foi : on prend le contexte renvoyé. */
    @Test
    void fallsBackToTheFirstContextWhenNoTenantIsConfigured() {
        properties.setTenantId(null);
        when(kernelCoreAuthAdapter.forgotPassword("admin@x.com"))
                .thenReturn(Mono.just(discovered("sel-1", List.of(context("ctx-premier", "tenant-a")))));
        when(kernelCoreAuthAdapter.issuePasswordReset(anyString(), anyString()))
                .thenReturn(Mono.just(challenge("PREVIEW_ONLY")));

        StepVerifier.create(authService.forgotPassword("admin@x.com")).expectNextCount(1).verifyComplete();

        verify(kernelCoreAuthAdapter).issuePasswordReset("sel-1", "ctx-premier");
    }

    /** Une déconnexion ne doit jamais échouer côté portail, même si KernelCore est en panne. */
    @Test
    void logoutSucceedsEvenWhenKernelCoreFails() {
        when(kernelCoreAuthAdapter.logout("jwt", "refresh"))
                .thenReturn(Mono.error(new IllegalStateException("KernelCore down")));

        StepVerifier.create(authService.logout("jwt", "refresh")).verifyComplete();
    }
}
