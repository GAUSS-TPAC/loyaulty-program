package com.yowyob.loyalty.application.auth;

import com.yowyob.loyalty.infrastructure.kernelcore.adapter.KernelCoreActorAdapter;
import com.yowyob.loyalty.infrastructure.kernelcore.adapter.KernelCoreAuthAdapter;
import com.yowyob.loyalty.infrastructure.kernelcore.adapter.KernelCoreTenantAdapter;
import com.yowyob.loyalty.infrastructure.kernelcore.config.KernelCoreProperties;
import com.yowyob.loyalty.infrastructure.kernelcore.dto.KernelDiscoveredContextDto;
import com.yowyob.loyalty.infrastructure.kernelcore.dto.KernelLoginResultDto;
import com.yowyob.loyalty.infrastructure.kernelcore.dto.KernelOrganizationSummaryDto;
import com.yowyob.loyalty.shared.exception.OrganizationNotAccessibleException;
import com.yowyob.loyalty.shared.exception.OrganizationSelectionRequiredException;
import com.yowyob.loyalty.shared.exception.RegistrationFailedException;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Authentification des administrateurs de tenant, déléguée à KernelCore auth-core
 * (POST /api/auth/login, tenant-scopé au tenant plateforme KernelCore de ce déploiement).
 *
 * Le JWT renvoyé par un login tenant-scopé ne porte pas de claim d'organisation : le
 * modèle multi-tenant de ce backend associe un "tenant" loyalty à une organisation
 * KernelCore, donc l'organisation cible doit être résolue explicitement à la connexion
 * et propagée par le client (header X-Organization-Id) sur les appels suivants — voir
 * TenantResolutionFilter et ApiKeyResolutionFilter.
 *
 * Quand le compte a le MFA actif, auth-core ne renvoie pas de jeton au login : un code
 * OTP part par email et doit être confirmé via {@link #confirmMfa} (deux étapes).
 */
@Service
public class AuthService {

    private final KernelCoreAuthAdapter kernelCoreAuthAdapter;
    private final KernelCoreProperties kernelCoreProperties;
    private final KernelCoreActorAdapter kernelCoreActorAdapter;
    private final KernelCoreTenantAdapter kernelCoreTenantAdapter;

    public AuthService(KernelCoreAuthAdapter kernelCoreAuthAdapter, KernelCoreProperties kernelCoreProperties,
                        KernelCoreActorAdapter kernelCoreActorAdapter, KernelCoreTenantAdapter kernelCoreTenantAdapter) {
        this.kernelCoreAuthAdapter = kernelCoreAuthAdapter;
        this.kernelCoreProperties = kernelCoreProperties;
        this.kernelCoreActorAdapter = kernelCoreActorAdapter;
        this.kernelCoreTenantAdapter = kernelCoreTenantAdapter;
    }

    /**
     * @param organizationId organisation KernelCore explicitement choisie par l'appelant
     *                        (optionnelle si l'acteur n'a accès qu'à une seule organisation)
     * @return soit un {@link LoginOutcome} authentifié, soit un défi MFA : KernelCore a envoyé
     *         un code par email et le client doit le confirmer via {@link #confirmMfa}.
     */
    public Mono<LoginOutcome> login(String email, String password, String organizationId) {
        return resolveTenantId(email, password, organizationId)
                .flatMap(tenantId -> kernelCoreAuthAdapter.login(tenantId, email, password))
                .flatMap(result -> {
                    if (result.mfaRequired()) {
                        return Mono.just(LoginOutcome.mfaRequired(result.mfaToken(), result.mfaChannel()));
                    }
                    return toAuthenticatedOutcome(result, organizationId, email);
                });
    }

    /** Deuxième étape du login MFA : confirme le code OTP reçu par email. */
    public Mono<AuthResult> confirmMfa(String mfaToken, String code, String organizationId) {
        return kernelCoreAuthAdapter.confirmMfaLogin(kernelCoreProperties.getTenantId(), mfaToken, code)
                .flatMap(result -> toAuthenticatedOutcome(result, organizationId, null))
                .map(LoginOutcome::result);
    }

    /**
     * L'inscription publique (sign-up) ne crée qu'un compte, jamais d'organisation :
     * premier login sans organisation ni choix explicite -> on en provisionne une.
     */
    private Mono<LoginOutcome> toAuthenticatedOutcome(KernelLoginResultDto result, String organizationId, String email) {
        boolean hasNoChoice = (organizationId == null || organizationId.isBlank());
        if (result.organizations().isEmpty() && hasNoChoice) {
            return provisionDefaultOrganization(result.accessToken(), email)
                    .map(LoginOutcome::authenticated);
        }
        return Mono.just(LoginOutcome.authenticated(resolveOrganization(result, organizationId)));
    }

    /**
     * Auto-provisionnement d'un espace de travail au premier login d'un compte inscrit en
     * self-service : POST /api/actors/me (businessActorId) puis POST /api/organizations.
     */
    private Mono<AuthResult> provisionDefaultOrganization(String accessToken, String email) {
        return kernelCoreActorAdapter.getMyProfile(accessToken)
                .flatMap(actor -> {
                    String suffix = UUID.randomUUID().toString().substring(0, 8).toUpperCase();
                    String code = "ORG-" + suffix;
                    String fallback = email != null && !email.isBlank() ? email : code;
                    String name = actor.getName() != null && !actor.getName().isBlank() ? actor.getName() : fallback;
                    return kernelCoreTenantAdapter.createOrganization(accessToken, actor.getId(), code, name, name);
                })
                .map(org -> new AuthResult(accessToken, org.getId().toString(), org.getCode(), org.resolveName()));
    }

    /**
     * Le tenant plateforme KernelCore est normalement fixé par configuration
     * (app.kernel-core.tenant-id / KERNEL_TENANT_ID). À défaut, il est découvert via
     * POST /api/auth/discover-contexts : KernelCore refuse un login sans X-Tenant-Id
     * (TENANT_REQUIRED) et exige ce flux en deux temps.
     */
    private Mono<String> resolveTenantId(String email, String password, String requestedOrganizationId) {
        String configured = kernelCoreProperties.getTenantId();
        if (configured != null && !configured.isBlank()) {
            return Mono.just(configured);
        }
        return kernelCoreAuthAdapter.discoverContexts(email, password)
                .map(contexts -> selectContext(contexts, requestedOrganizationId).getTenantId());
    }

    private KernelDiscoveredContextDto selectContext(List<KernelDiscoveredContextDto> contexts,
                                                     String requestedOrganizationId) {
        if (contexts.isEmpty()) {
            throw new OrganizationNotAccessibleException("Aucun contexte de connexion accessible pour ce compte");
        }
        if (contexts.size() == 1) {
            return contexts.get(0);
        }
        if (requestedOrganizationId != null && !requestedOrganizationId.isBlank()) {
            return contexts.stream()
                    .filter(c -> c.getOrganizations().stream()
                            .anyMatch(o -> requestedOrganizationId.equals(o.getOrganizationId())))
                    .findFirst()
                    .orElseThrow(() -> new OrganizationNotAccessibleException(
                            "L'organisation demandée n'est accessible dans aucun contexte de ce compte"));
        }
        Map<String, Object> available = Map.of(
                "organizations", contexts.stream()
                        .flatMap(c -> c.getOrganizations().stream())
                        .map(o -> Map.of(
                                "organizationId", String.valueOf(o.getOrganizationId()),
                                "organizationCode", String.valueOf(o.getOrganizationCode()),
                                "displayName", String.valueOf(o.getDisplayName())))
                        .collect(Collectors.toList()));
        throw new OrganizationSelectionRequiredException(
                "Ce compte a accès à plusieurs contextes ; précisez organizationId", available);
    }

    private AuthResult resolveOrganization(KernelLoginResultDto result, String requestedOrganizationId) {
        List<KernelOrganizationSummaryDto> organizations = result.organizations();

        if (requestedOrganizationId != null && !requestedOrganizationId.isBlank()) {
            KernelOrganizationSummaryDto match = organizations.stream()
                    .filter(o -> requestedOrganizationId.equals(o.getOrganizationId()))
                    .findFirst()
                    .orElseThrow(() -> new OrganizationNotAccessibleException(
                            "L'organisation demandée n'est pas accessible à cet acteur"));
            return AuthResult.from(result.accessToken(), match);
        }

        if (organizations.isEmpty()) {
            throw new OrganizationNotAccessibleException("Aucune organisation accessible pour cet acteur");
        }
        if (organizations.size() == 1) {
            return AuthResult.from(result.accessToken(), organizations.get(0));
        }

        Map<String, Object> available = Map.of(
                "organizations", organizations.stream()
                        .map(o -> Map.of(
                                "organizationId", String.valueOf(o.getOrganizationId()),
                                "organizationCode", String.valueOf(o.getOrganizationCode()),
                                "displayName", String.valueOf(o.getDisplayName())))
                        .collect(Collectors.toList()));
        throw new OrganizationSelectionRequiredException(
                "Cet acteur a accès à plusieurs organisations ; précisez organizationId", available);
    }

    public record AuthResult(String token, String organizationId, String organizationCode, String organizationName) {
        static AuthResult from(String token, KernelOrganizationSummaryDto org) {
            return new AuthResult(token, org.getOrganizationId(), org.getOrganizationCode(), org.getDisplayName());
        }
    }

    /** Issue d'un login : authentifié directement, ou défi MFA à confirmer (code envoyé par email). */
    public record LoginOutcome(AuthResult result, String mfaToken, String mfaChannel) {
        static LoginOutcome authenticated(AuthResult result) {
            return new LoginOutcome(result, null, null);
        }

        static LoginOutcome mfaRequired(String mfaToken, String mfaChannel) {
            return new LoginOutcome(null, mfaToken, mfaChannel);
        }

        public boolean isMfaRequired() {
            return result == null;
        }
    }

    /**
     * Inscription publique (page "Ouvrir un compte") : crée un compte KernelCore sous
     * l'organisation fixe de ce déploiement (app.kernel-core.organization-code). Le compte
     * reste EMAIL_VERIFICATION_REQUIRED — le login échouera tant que l'email n'est pas
     * confirmé (email envoyé par KernelCore).
     */
    public Mono<RegisterResult> register(String firstName, String lastName, String email, String password) {
        String organizationCode = kernelCoreProperties.getOrganizationCode();
        if (organizationCode == null || organizationCode.isBlank()) {
            return Mono.error(new RegistrationFailedException(
                    "Inscription indisponible : organisation cible non configurée (app.kernel-core.organization-code)"));
        }
        return kernelCoreAuthAdapter.discoverSignUpSelection(organizationCode)
                .flatMap(selection -> kernelCoreAuthAdapter.signUp(
                        selection, usernameFrom(email), firstName, lastName, email, password))
                .map(result -> new RegisterResult(result.getEmail(), result.getStatus(), result.isEmailVerified()));
    }

    /**
     * KernelCore impose un {@code username} à l'inscription alors que notre formulaire ne le
     * collecte pas : on le dérive de la partie locale de l'email, en respectant le motif
     * d'auth-core {@code ^[A-Za-z0-9](?:[A-Za-z0-9._-]{1,30}[A-Za-z0-9])$} (3 à 32 caractères,
     * bornes alphanumériques). Deux adresses de domaines différents mais de même partie locale
     * produisent le même identifiant : KernelCore rejette alors le doublon, et son message
     * remonte tel quel à l'utilisateur.
     */
    static String usernameFrom(String email) {
        String local = email.substring(0, Math.max(email.indexOf('@'), 0));
        String cleaned = local.replaceAll("[^A-Za-z0-9._-]", "-")
                .replaceAll("^[^A-Za-z0-9]+", "")
                .replaceAll("[^A-Za-z0-9]+$", "");
        if (cleaned.length() > 32) {
            cleaned = cleaned.substring(0, 32).replaceAll("[^A-Za-z0-9]+$", "");
        }
        // Motif et longueur minimale garantis même pour une partie locale vide ou trop courte.
        return cleaned.length() >= 3 ? cleaned : (cleaned + "user").substring(0, 4);
    }

    public record RegisterResult(String email, String status, boolean emailVerified) {}
}
