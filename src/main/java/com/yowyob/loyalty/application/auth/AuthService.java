package com.yowyob.loyalty.application.auth;

import com.yowyob.loyalty.infrastructure.kernelcore.adapter.KernelCoreActorAdapter;
import com.yowyob.loyalty.infrastructure.kernelcore.adapter.KernelCoreAuthAdapter;
import com.yowyob.loyalty.infrastructure.kernelcore.adapter.KernelCoreTenantAdapter;
import com.yowyob.loyalty.infrastructure.kernelcore.config.KernelCoreProperties;
import com.yowyob.loyalty.infrastructure.kernelcore.dto.KernelDiscoveredContextDto;
import com.yowyob.loyalty.infrastructure.kernelcore.dto.KernelForgotPasswordResponseDto;
import com.yowyob.loyalty.infrastructure.kernelcore.dto.KernelIssuedAuthChallengeDto;
import com.yowyob.loyalty.infrastructure.kernelcore.dto.KernelPasswordResetContextDto;
import com.yowyob.loyalty.infrastructure.kernelcore.dto.KernelRefreshTokenResponseDto;
import com.yowyob.loyalty.infrastructure.kernelcore.dto.KernelLoginResultDto;
import com.yowyob.loyalty.infrastructure.kernelcore.dto.KernelOrganizationSummaryDto;
import com.yowyob.loyalty.infrastructure.kernelcore.dto.KernelUserAccountDto;
import com.yowyob.loyalty.shared.exception.OrganizationNotAccessibleException;
import com.yowyob.loyalty.shared.exception.OrganizationSelectionRequiredException;
import com.yowyob.loyalty.shared.exception.RegistrationFailedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

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
            return provisionDefaultOrganization(result.accessToken(), email, result.session())
                    .map(LoginOutcome::authenticated);
        }
        return Mono.just(LoginOutcome.authenticated(resolveOrganization(result, organizationId)));
    }

    /**
     * Auto-provisionnement d'un espace de travail au premier login d'un compte inscrit en
     * self-service : POST /api/actors/me (businessActorId) puis POST /api/organizations.
     */
    private Mono<AuthResult> provisionDefaultOrganization(String accessToken, String email,
                                                          KernelLoginResultDto.Session session) {
        return kernelCoreActorAdapter.getMyProfile(accessToken)
                .flatMap(actor -> {
                    String suffix = UUID.randomUUID().toString().substring(0, 8).toUpperCase();
                    String code = "ORG-" + suffix;
                    String fallback = email != null && !email.isBlank() ? email : code;
                    String name = actor.getName() != null && !actor.getName().isBlank() ? actor.getName() : fallback;
                    return kernelCoreTenantAdapter.createOrganization(accessToken, actor.getId(), code, name, name);
                })
                .map(org -> new AuthResult(accessToken, org.getId().toString(), org.getCode(), org.resolveName(), session));
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
            return AuthResult.from(result.accessToken(), match, result.session());
        }

        if (organizations.isEmpty()) {
            throw new OrganizationNotAccessibleException("Aucune organisation accessible pour cet acteur");
        }
        if (organizations.size() == 1) {
            return AuthResult.from(result.accessToken(), organizations.get(0), result.session());
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

    /**
     * @param session durée de vie et refresh token émis par KernelCore, à propager au client
     *                pour qu'il rafraîchisse avant expiration plutôt que sur un 401.
     */
    public record AuthResult(String token, String organizationId, String organizationCode, String organizationName,
                             KernelLoginResultDto.Session session) {
        static AuthResult from(String token, KernelOrganizationSummaryDto org, KernelLoginResultDto.Session session) {
            return new AuthResult(token, org.getOrganizationId(), org.getOrganizationCode(), org.getDisplayName(), session);
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

    // ── Cycle de vie des credentials ─────────────────────────────────────────────────

    /**
     * Mot de passe oublié, en deux appels KernelCore enchaînés ici pour que le portail n'ait
     * qu'un seul endpoint à appeler : forgot-password (résolution des comptes + jeton court)
     * puis password-reset/issue (émission du jeton de reset et envoi de l'email).
     *
     * Réponse volontairement identique qu'un compte existe ou non : la remontée d'un
     * "compte inconnu" transformerait ce formulaire en oracle d'énumération d'adresses.
     */
    public Mono<PasswordResetRequest> forgotPassword(String email) {
        return kernelCoreAuthAdapter.forgotPassword(email)
                .flatMap(response -> {
                    String selectionToken = response.getSelectionToken();
                    KernelPasswordResetContextDto context = selectResetContext(response);
                    if (selectionToken == null || selectionToken.isBlank() || context == null) {
                        return Mono.just(PasswordResetRequest.noAccount());
                    }
                    return kernelCoreAuthAdapter.issuePasswordReset(selectionToken, context.getContextId())
                            .doOnNext(AuthService::warnIfNotDelivered)
                            .map(PasswordResetRequest::issued);
                });
    }

    /**
     * Un même email peut exister dans plusieurs tenants KernelCore. Ce déploiement n'en sert
     * qu'un (app.kernel-core.tenant-id) : on réinitialise le compte de ce tenant, et on ne
     * retombe sur le premier contexte que si le tenant n'est pas fixé par configuration.
     */
    private KernelPasswordResetContextDto selectResetContext(KernelForgotPasswordResponseDto response) {
        List<KernelPasswordResetContextDto> contexts = response.getContexts();
        if (contexts == null || contexts.isEmpty()) {
            return null;
        }
        String tenantId = kernelCoreProperties.getTenantId();
        if (tenantId != null && !tenantId.isBlank()) {
            return contexts.stream()
                    .filter(c -> tenantId.equals(c.getTenantId()))
                    .findFirst()
                    .orElse(contexts.get(0));
        }
        return contexts.get(0);
    }

    /** Dernière étape du parcours « mot de passe oublié » : le jeton vient du lien reçu par email. */
    public Mono<KernelUserAccountDto> resetPassword(String resetToken, String newPassword) {
        return kernelCoreAuthAdapter.resetPassword(resetToken, newPassword);
    }

    /** Changement de mot de passe depuis le portail, par l'utilisateur déjà connecté. */
    public Mono<KernelUserAccountDto> changePassword(String accessToken, String currentPassword, String newPassword) {
        return kernelCoreAuthAdapter.changePassword(accessToken, currentPassword, newPassword);
    }

    /**
     * Renvoi du mail de vérification : sans session, puisqu'un compte non vérifié ne peut
     * précisément pas se connecter. Réponse neutre, pour la même raison que forgotPassword.
     */
    public Mono<PasswordResetRequest> resendEmailVerification(String email) {
        return kernelCoreAuthAdapter.resendEmailVerification(email)
                .doOnNext(AuthService::warnIfNotDelivered)
                .map(PasswordResetRequest::issued);
    }

    /**
     * PREVIEW_ONLY signifie que KernelCore n'a aucun provider SMTP configuré : le jeton a bien
     * été émis mais aucun email ne partira, et l'utilisateur attendra un message qui n'arrivera
     * jamais. Invisible côté client (réponse volontairement neutre), donc tracé ici.
     */
    private static void warnIfNotDelivered(KernelIssuedAuthChallengeDto challenge) {
        if ("PREVIEW_ONLY".equalsIgnoreCase(challenge.getDeliveryMode())) {
            log.warn("KernelCore a émis un défi en mode PREVIEW_ONLY : aucun email ne sera envoyé "
                    + "(provider SMTP non configuré côté KernelCore)");
        }
    }

    /** Confirmation de l'adresse email à partir du jeton porté par le lien reçu. */
    public Mono<KernelUserAccountDto> confirmEmailVerification(String verificationToken) {
        return kernelCoreAuthAdapter.confirmEmailVerification(verificationToken);
    }

    // ── Cycle de vie de la session ───────────────────────────────────────────────────

    /** Échange un refresh token contre une nouvelle paire de jetons (l'ancien est révoqué). */
    public Mono<KernelRefreshTokenResponseDto> refresh(String refreshToken) {
        return kernelCoreAuthAdapter.refresh(refreshToken);
    }

    /**
     * Déconnexion best-effort : le client purge ses jetons dans tous les cas, une panne
     * KernelCore ne doit pas laisser l'utilisateur coincé dans une session qu'il veut quitter.
     */
    public Mono<Void> logout(String accessToken, String refreshToken) {
        return kernelCoreAuthAdapter.logout(accessToken, refreshToken)
                .onErrorResume(e -> Mono.empty());
    }

    /**
     * Issue d'une demande de réinitialisation ou de renvoi de vérification, sans jamais
     * révéler si un compte correspond.
     *
     * @param deliveryMode EMAIL/SMTP quand KernelCore a réellement envoyé le message,
     *                     PREVIEW_ONLY quand aucun provider SMTP n'est configuré chez lui —
     *                     dans ce cas aucun email ne partira, symptôme à surveiller en prod.
     */
    public record PasswordResetRequest(String deliveryMode, Integer expiresInSeconds) {

        static PasswordResetRequest noAccount() {
            return new PasswordResetRequest(null, null);
        }

        static PasswordResetRequest issued(KernelIssuedAuthChallengeDto challenge) {
            return new PasswordResetRequest(challenge.getDeliveryMode(), challenge.getExpiresInSeconds());
        }
    }
}
