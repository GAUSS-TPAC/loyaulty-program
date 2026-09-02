package com.yowyob.loyalty.infrastructure.kernelcore.adapter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yowyob.loyalty.infrastructure.kernelcore.dto.KernelApiResponse;
import com.yowyob.loyalty.infrastructure.kernelcore.dto.KernelChangePasswordRequestDto;
import com.yowyob.loyalty.infrastructure.kernelcore.dto.KernelConfirmEmailVerificationRequestDto;
import com.yowyob.loyalty.infrastructure.kernelcore.dto.KernelConfirmMfaLoginRequestDto;
import com.yowyob.loyalty.infrastructure.kernelcore.dto.KernelDiscoverContextsResponseDto;
import com.yowyob.loyalty.infrastructure.kernelcore.dto.KernelDiscoverSignUpContextsRequestDto;
import com.yowyob.loyalty.infrastructure.kernelcore.dto.KernelDiscoverSignUpContextsResponseDto;
import com.yowyob.loyalty.infrastructure.kernelcore.dto.KernelDiscoveredContextDto;
import com.yowyob.loyalty.infrastructure.kernelcore.dto.KernelForgotPasswordRequest;
import com.yowyob.loyalty.infrastructure.kernelcore.dto.KernelForgotPasswordResponseDto;
import com.yowyob.loyalty.infrastructure.kernelcore.dto.KernelIssuePasswordResetRequestDto;
import com.yowyob.loyalty.infrastructure.kernelcore.dto.KernelIssuedAuthChallengeDto;
import com.yowyob.loyalty.infrastructure.kernelcore.dto.KernelLoginRequestDto;
import com.yowyob.loyalty.infrastructure.kernelcore.dto.KernelLoginResponseDto;
import com.yowyob.loyalty.infrastructure.kernelcore.dto.KernelLoginResultDto;
import com.yowyob.loyalty.infrastructure.kernelcore.dto.KernelOrganizationSummaryDto;
import com.yowyob.loyalty.infrastructure.kernelcore.dto.KernelPublicSignUpRequestDto;
import com.yowyob.loyalty.infrastructure.kernelcore.dto.KernelRefreshTokenRequestDto;
import com.yowyob.loyalty.infrastructure.kernelcore.dto.KernelRefreshTokenResponseDto;
import com.yowyob.loyalty.infrastructure.kernelcore.dto.KernelResetPasswordRequestDto;
import com.yowyob.loyalty.infrastructure.kernelcore.dto.KernelSignUpResultDto;
import com.yowyob.loyalty.infrastructure.kernelcore.dto.KernelUserAccountDto;
import com.yowyob.loyalty.shared.exception.EmailVerificationFailedException;
import com.yowyob.loyalty.shared.exception.InvalidCredentialsException;
import com.yowyob.loyalty.shared.exception.KernelCoreUnavailableException;
import com.yowyob.loyalty.shared.exception.PasswordResetFailedException;
import com.yowyob.loyalty.shared.exception.RegistrationFailedException;
import com.yowyob.loyalty.shared.exception.SessionExpiredException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatusCode;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * Authentification locale tenant-scopée via KernelCore auth-core.
 * Endpoint : POST /api/auth/login (X-Tenant-Id + {principal, password} -> ApiResponse&lt;LoginResponse&gt;
 * portant le JWT RS256 et les organisations accessibles à l'acteur).
 * X-Client-Id/X-Api-Key (identité du backend consommateur) sont déjà portés par défaut
 * par kernelCoreWebClient (voir KernelCoreConfig).
 */
public class KernelCoreAuthAdapter {

    private static final Logger log = LoggerFactory.getLogger(KernelCoreAuthAdapter.class);

    /** Lecture des corps d'erreur seulement : aucune configuration partagée à respecter. */
    private static final ObjectMapper ERROR_MAPPER = new ObjectMapper();

    private static final ParameterizedTypeReference<KernelApiResponse<KernelLoginResponseDto>> LOGIN_TYPE =
            new ParameterizedTypeReference<>() {};

    private static final ParameterizedTypeReference<KernelApiResponse<KernelDiscoverContextsResponseDto>> DISCOVER_TYPE =
            new ParameterizedTypeReference<>() {};

    private static final ParameterizedTypeReference<KernelApiResponse<KernelDiscoverSignUpContextsResponseDto>> DISCOVER_SIGNUP_TYPE =
            new ParameterizedTypeReference<>() {};

    private static final ParameterizedTypeReference<KernelApiResponse<KernelSignUpResultDto>> SIGNUP_TYPE =
            new ParameterizedTypeReference<>() {};

    private static final ParameterizedTypeReference<KernelApiResponse<KernelForgotPasswordResponseDto>> FORGOT_TYPE =
            new ParameterizedTypeReference<>() {};

    private static final ParameterizedTypeReference<KernelApiResponse<KernelIssuedAuthChallengeDto>> CHALLENGE_TYPE =
            new ParameterizedTypeReference<>() {};

    private static final ParameterizedTypeReference<KernelApiResponse<KernelUserAccountDto>> ACCOUNT_TYPE =
            new ParameterizedTypeReference<>() {};

    private static final ParameterizedTypeReference<KernelApiResponse<KernelRefreshTokenResponseDto>> REFRESH_TYPE =
            new ParameterizedTypeReference<>() {};

    private static final ParameterizedTypeReference<KernelApiResponse<Object>> VOID_TYPE =
            new ParameterizedTypeReference<>() {};

    private final WebClient kernelCoreWebClient;

    public KernelCoreAuthAdapter(WebClient kernelCoreWebClient) {
        this.kernelCoreWebClient = kernelCoreWebClient;
    }

    public Mono<KernelLoginResultDto> login(String tenantId, String principal, String password) {
        return kernelCoreWebClient.post()
                .uri("/api/auth/login")
                .header("X-Tenant-Id", tenantId)
                .bodyValue(new KernelLoginRequestDto(principal, password))
                .retrieve()
                .onStatus(status -> status.value() == 401 || status.value() == 403,
                        resp -> Mono.error(new InvalidCredentialsException("Email ou mot de passe incorrect")))
                .onStatus(status -> status.value() == 429,
                        resp -> Mono.error(new InvalidCredentialsException(
                                "Un code vient déjà d'être envoyé — patientez avant de réessayer")))
                .onStatus(HttpStatusCode::is4xxClientError,
                        resp -> resp.bodyToMono(String.class).defaultIfEmpty("")
                                .flatMap(body -> Mono.error(new InvalidCredentialsException("Authentification refusée: " + explain(body)))))
                .onStatus(HttpStatusCode::is5xxServerError,
                        resp -> Mono.error(new KernelCoreUnavailableException("KernelCore indisponible pour l'authentification")))
                .bodyToMono(LOGIN_TYPE)
                .flatMap(this::unwrapLogin)
                .onErrorMap(WebClientRequestException.class, ex -> new KernelCoreUnavailableException(
                        "KernelCore injoignable (authentification): " + ex.getMessage()))
                .doOnError(e -> log.warn("Échec authentification KernelCore: {}", e.getMessage()));
    }

    /**
     * Découverte des contextes de connexion (tenants plateforme) accessibles au compte,
     * via POST /api/auth/discover-contexts — pas de X-Tenant-Id requis. Utilisée quand le
     * tenant plateforme n'est pas fixé par configuration (app.kernel-core.tenant-id).
     */
    public Mono<List<KernelDiscoveredContextDto>> discoverContexts(String principal, String password) {
        return kernelCoreWebClient.post()
                .uri("/api/auth/discover-contexts")
                .bodyValue(new KernelLoginRequestDto(principal, password))
                .retrieve()
                .onStatus(status -> status.value() == 401 || status.value() == 403,
                        resp -> Mono.error(new InvalidCredentialsException("Email ou mot de passe incorrect")))
                .onStatus(HttpStatusCode::is4xxClientError,
                        resp -> resp.bodyToMono(String.class).defaultIfEmpty("")
                                .flatMap(body -> Mono.error(new InvalidCredentialsException("Découverte des contextes refusée: " + explain(body)))))
                .onStatus(HttpStatusCode::is5xxServerError,
                        resp -> Mono.error(new KernelCoreUnavailableException("KernelCore indisponible pour la découverte des contextes")))
                .bodyToMono(DISCOVER_TYPE)
                .flatMap(this::unwrapContexts)
                .onErrorMap(WebClientRequestException.class, ex -> new KernelCoreUnavailableException(
                        "KernelCore injoignable (découverte des contextes): " + ex.getMessage()))
                .doOnError(e -> log.warn("Échec découverte des contextes KernelCore: {}", e.getMessage()));
    }

    private Mono<List<KernelDiscoveredContextDto>> unwrapContexts(KernelApiResponse<KernelDiscoverContextsResponseDto> response) {
        if (!response.isSuccess() || response.getData() == null) {
            return Mono.error(new KernelCoreUnavailableException("Réponse KernelCore invalide pour /api/auth/discover-contexts"));
        }
        return Mono.just(response.getData().getContexts());
    }

    /**
     * Sélection d'inscription : le jeton court et le contexte auquel il se rapporte.
     * KernelCore refuse le jeton sans son contextId, les deux doivent voyager ensemble.
     */
    public record SignUpSelection(String selectionToken, String contextId) {}

    /**
     * Découverte du contexte d'inscription pour une organisation donnée (code fixe de ce
     * déploiement, app.kernel-core.organization-code), via POST /api/auth/discover-sign-up-contexts.
     * Le couple renvoyé est réutilisé par signUp().
     */
    public Mono<SignUpSelection> discoverSignUpSelection(String organizationCode) {
        return kernelCoreWebClient.post()
                .uri("/api/auth/discover-sign-up-contexts")
                .bodyValue(new KernelDiscoverSignUpContextsRequestDto(organizationCode))
                .retrieve()
                .onStatus(HttpStatusCode::is4xxClientError,
                        resp -> resp.bodyToMono(String.class).defaultIfEmpty("")
                                .flatMap(body -> Mono.error(new RegistrationFailedException(
                                        "Découverte du contexte d'inscription refusée: " + explain(body)))))
                .onStatus(HttpStatusCode::is5xxServerError,
                        resp -> Mono.error(new KernelCoreUnavailableException(
                                "KernelCore indisponible pour la découverte du contexte d'inscription")))
                .bodyToMono(DISCOVER_SIGNUP_TYPE)
                .flatMap(response -> {
                    if (!response.isSuccess() || response.getData() == null
                            || response.getData().getSelectionToken() == null
                            || response.getData().getContexts().isEmpty()) {
                        return Mono.<SignUpSelection>error(new KernelCoreUnavailableException(
                                "Réponse KernelCore invalide pour /api/auth/discover-sign-up-contexts"));
                    }
                    // Le code d'organisation est unique : la découverte ne renvoie qu'un contexte.
                    return Mono.just(new SignUpSelection(
                            response.getData().getSelectionToken(),
                            response.getData().getContexts().get(0).getContextId()));
                })
                .onErrorMap(WebClientRequestException.class, ex -> new KernelCoreUnavailableException(
                        "KernelCore injoignable (découverte du contexte d'inscription): " + ex.getMessage()))
                .doOnError(e -> log.warn("Échec découverte du contexte d'inscription KernelCore: {}", e.getMessage()));
    }

    /**
     * Inscription publique via POST /api/auth/sign-up. Le compte créé reste
     * EMAIL_VERIFICATION_REQUIRED (login refusé) tant que l'adresse n'est pas confirmée.
     */
    public Mono<KernelSignUpResultDto> signUp(SignUpSelection selection, String username, String firstName,
                                               String lastName, String email, String password) {
        return kernelCoreWebClient.post()
                .uri("/api/auth/sign-up")
                .bodyValue(new KernelPublicSignUpRequestDto(selection.selectionToken(), selection.contextId(),
                        username, firstName, lastName, email, password))
                .retrieve()
                .onStatus(HttpStatusCode::is4xxClientError,
                        resp -> resp.bodyToMono(String.class).defaultIfEmpty("")
                                .flatMap(body -> Mono.error(new RegistrationFailedException(
                                        "Inscription refusée: " + explain(body)))))
                // KernelCore rend un 500 — et non un 409 — sur doublon ("An actor already exists
                // with email: …"). Traduire tout 5xx en indisponibilité afficherait "KernelCore
                // indisponible" à un utilisateur dont le seul tort est d'avoir deja un compte, en
                // masquant la seule information exploitable. On ne garde l'indisponibilité que
                // lorsque le corps est vide, cas d'une vraie panne.
                .onStatus(HttpStatusCode::is5xxServerError,
                        resp -> resp.bodyToMono(String.class).defaultIfEmpty("")
                                .flatMap(body -> Mono.error(body.isBlank()
                                        ? new KernelCoreUnavailableException("KernelCore indisponible pour l'inscription")
                                        : new RegistrationFailedException("Inscription refusée: " + explain(body)))))
                .bodyToMono(SIGNUP_TYPE)
                .flatMap(response -> {
                    if (!response.isSuccess() || response.getData() == null) {
                        String message = response.getMessage() != null ? response.getMessage() : "réponse KernelCore invalide";
                        return Mono.<KernelSignUpResultDto>error(new RegistrationFailedException(message));
                    }
                    return Mono.just(response.getData());
                })
                .onErrorMap(WebClientRequestException.class, ex -> new KernelCoreUnavailableException(
                        "KernelCore injoignable (inscription): " + ex.getMessage()))
                .doOnError(e -> log.warn("Échec inscription KernelCore: {}", e.getMessage()));
    }

    /**
     * Confirme un défi MFA (code OTP reçu par email) et récupère le JWT.
     * Endpoint : POST /api/auth/login/mfa/confirm {mfaToken, code}.
     */
    public Mono<KernelLoginResultDto> confirmMfaLogin(String tenantId, String mfaToken, String code) {
        return kernelCoreWebClient.post()
                .uri("/api/auth/login/mfa/confirm")
                .header("X-Tenant-Id", tenantId)
                .bodyValue(new KernelConfirmMfaLoginRequestDto(mfaToken, code))
                .retrieve()
                .onStatus(status -> status.value() == 401 || status.value() == 403,
                        resp -> Mono.error(new InvalidCredentialsException("Code de vérification invalide ou expiré")))
                .onStatus(HttpStatusCode::is4xxClientError,
                        resp -> resp.bodyToMono(String.class).defaultIfEmpty("")
                                .flatMap(body -> Mono.error(new InvalidCredentialsException("Vérification refusée: " + explain(body)))))
                .onStatus(HttpStatusCode::is5xxServerError,
                        resp -> Mono.error(new KernelCoreUnavailableException("KernelCore indisponible pour la vérification MFA")))
                .bodyToMono(LOGIN_TYPE)
                .flatMap(this::unwrapAuthenticated)
                .doOnError(e -> log.warn("Échec confirmation MFA KernelCore: {}", e.getMessage()));
    }

    // ── Cycle de vie des credentials (mot de passe, vérification d'adresse email) ──────

    /**
     * Première étape de la réinitialisation : POST /api/auth/forgot-password résout tous les
     * comptes portant ce principal et émet un jeton court de sélection.
     *
     * KernelCore répond {@code success} même quand aucun compte ne correspond
     * ({@code matchingAccountCount=0}, {@code selectionToken=null}) : c'est volontaire, la
     * réponse ne doit pas permettre d'énumérer les comptes existants. L'appelant doit donc
     * traiter l'absence de jeton comme un cas normal, pas comme une erreur.
     */
    public Mono<KernelForgotPasswordResponseDto> forgotPassword(String principal) {
        return kernelCoreWebClient.post()
                .uri("/api/auth/forgot-password")
                .bodyValue(new KernelForgotPasswordRequest(principal))
                .retrieve()
                .onStatus(HttpStatusCode::is4xxClientError,
                        resp -> resp.bodyToMono(String.class).defaultIfEmpty("")
                                .flatMap(body -> Mono.error(new PasswordResetFailedException(
                                        "Demande de réinitialisation refusée: " + explain(body)))))
                .onStatus(HttpStatusCode::is5xxServerError,
                        resp -> Mono.error(new KernelCoreUnavailableException(
                                "KernelCore indisponible pour la réinitialisation de mot de passe")))
                .bodyToMono(FORGOT_TYPE)
                .flatMap(response -> unwrap(response, "/api/auth/forgot-password",
                        PasswordResetFailedException::new))
                .doOnError(e -> log.warn("Échec demande de réinitialisation KernelCore: {}", e.getMessage()));
    }

    /**
     * Deuxième étape : POST /api/auth/password-reset/issue émet le jeton de reset et envoie
     * l'email. Si aucun provider SMTP n'est configuré côté KernelCore, {@code deliveryMode}
     * vaut PREVIEW_ONLY et le jeton n'est renvoyé que dans challengeTokenPreview.
     */
    public Mono<KernelIssuedAuthChallengeDto> issuePasswordReset(String selectionToken, String contextId) {
        return kernelCoreWebClient.post()
                .uri("/api/auth/password-reset/issue")
                .bodyValue(new KernelIssuePasswordResetRequestDto(selectionToken, contextId))
                .retrieve()
                .onStatus(HttpStatusCode::is4xxClientError,
                        resp -> resp.bodyToMono(String.class).defaultIfEmpty("")
                                .flatMap(body -> Mono.error(new PasswordResetFailedException(
                                        "Émission du jeton de réinitialisation refusée: " + explain(body)))))
                .onStatus(HttpStatusCode::is5xxServerError,
                        resp -> Mono.error(new KernelCoreUnavailableException(
                                "KernelCore indisponible pour l'envoi du lien de réinitialisation")))
                .bodyToMono(CHALLENGE_TYPE)
                .flatMap(response -> unwrap(response, "/api/auth/password-reset/issue",
                        PasswordResetFailedException::new))
                .doOnError(e -> log.warn("Échec émission du jeton de réinitialisation KernelCore: {}", e.getMessage()));
    }

    /** Dernière étape : POST /api/auth/reset-password avec le jeton reçu par email. */
    public Mono<KernelUserAccountDto> resetPassword(String resetToken, String newPassword) {
        return kernelCoreWebClient.post()
                .uri("/api/auth/reset-password")
                .bodyValue(new KernelResetPasswordRequestDto(resetToken, newPassword))
                .retrieve()
                .onStatus(HttpStatusCode::is4xxClientError,
                        resp -> resp.bodyToMono(String.class).defaultIfEmpty("")
                                .flatMap(body -> Mono.error(new PasswordResetFailedException(
                                        "Réinitialisation refusée (lien expiré ou déjà utilisé): " + explain(body)))))
                .onStatus(HttpStatusCode::is5xxServerError,
                        resp -> Mono.error(new KernelCoreUnavailableException(
                                "KernelCore indisponible pour la réinitialisation de mot de passe")))
                .bodyToMono(ACCOUNT_TYPE)
                .flatMap(response -> unwrap(response, "/api/auth/reset-password",
                        PasswordResetFailedException::new))
                .doOnError(e -> log.warn("Échec réinitialisation de mot de passe KernelCore: {}", e.getMessage()));
    }

    /**
     * Changement de mot de passe par l'utilisateur connecté (Bearer obligatoire).
     * Un mot de passe courant erroné remonte en 4xx : traduit en InvalidCredentialsException
     * pour que le portail affiche « mot de passe actuel incorrect » et non une erreur générique.
     */
    public Mono<KernelUserAccountDto> changePassword(String accessToken, String currentPassword, String newPassword) {
        return kernelCoreWebClient.post()
                .uri("/api/auth/change-password")
                .header("Authorization", "Bearer " + accessToken)
                .bodyValue(new KernelChangePasswordRequestDto(currentPassword, newPassword))
                .retrieve()
                .onStatus(status -> status.value() == 400 || status.value() == 401 || status.value() == 403,
                        resp -> resp.bodyToMono(String.class).defaultIfEmpty("")
                                .flatMap(body -> Mono.error(new InvalidCredentialsException(
                                        "Changement de mot de passe refusé: " + explain(body)))))
                .onStatus(HttpStatusCode::is4xxClientError,
                        resp -> resp.bodyToMono(String.class).defaultIfEmpty("")
                                .flatMap(body -> Mono.error(new InvalidCredentialsException(
                                        "Changement de mot de passe refusé: " + explain(body)))))
                .onStatus(HttpStatusCode::is5xxServerError,
                        resp -> Mono.error(new KernelCoreUnavailableException(
                                "KernelCore indisponible pour le changement de mot de passe")))
                .bodyToMono(ACCOUNT_TYPE)
                .flatMap(response -> unwrap(response, "/api/auth/change-password",
                        InvalidCredentialsException::new))
                .doOnError(e -> log.warn("Échec changement de mot de passe KernelCore: {}", e.getMessage()));
    }

    /**
     * Renvoi de l'email de vérification, sans session : le compte tout juste inscrit ne peut
     * pas se connecter tant que l'adresse n'est pas confirmée. KernelCore répond de façon
     * neutre (« If the account exists and is unverified… ») pour ne pas révéler l'existence
     * du compte — contrat vérifié en appel réel, il n'est pas décrit dans son OpenAPI.
     */
    public Mono<KernelIssuedAuthChallengeDto> resendEmailVerification(String principal) {
        return kernelCoreWebClient.post()
                .uri("/api/auth/email-verification/resend")
                .bodyValue(new KernelForgotPasswordRequest(principal))
                .retrieve()
                .onStatus(HttpStatusCode::is4xxClientError,
                        resp -> resp.bodyToMono(String.class).defaultIfEmpty("")
                                .flatMap(body -> Mono.error(new EmailVerificationFailedException(
                                        "Renvoi de la vérification refusé: " + explain(body)))))
                .onStatus(HttpStatusCode::is5xxServerError,
                        resp -> Mono.error(new KernelCoreUnavailableException(
                                "KernelCore indisponible pour la vérification d'adresse email")))
                .bodyToMono(CHALLENGE_TYPE)
                .flatMap(response -> unwrap(response, "/api/auth/email-verification/resend",
                        EmailVerificationFailedException::new))
                .doOnError(e -> log.warn("Échec renvoi de vérification email KernelCore: {}", e.getMessage()));
    }

    /** Confirmation de l'adresse email à partir du jeton porté par le lien reçu. */
    public Mono<KernelUserAccountDto> confirmEmailVerification(String verificationToken) {
        return kernelCoreWebClient.post()
                .uri("/api/auth/email-verification/confirm")
                .bodyValue(new KernelConfirmEmailVerificationRequestDto(verificationToken))
                .retrieve()
                .onStatus(HttpStatusCode::is4xxClientError,
                        resp -> resp.bodyToMono(String.class).defaultIfEmpty("")
                                .flatMap(body -> Mono.error(new EmailVerificationFailedException(
                                        "Vérification refusée (lien expiré ou déjà utilisé): " + explain(body)))))
                .onStatus(HttpStatusCode::is5xxServerError,
                        resp -> Mono.error(new KernelCoreUnavailableException(
                                "KernelCore indisponible pour la vérification d'adresse email")))
                .bodyToMono(ACCOUNT_TYPE)
                .flatMap(response -> unwrap(response, "/api/auth/email-verification/confirm",
                        EmailVerificationFailedException::new))
                .doOnError(e -> log.warn("Échec confirmation email KernelCore: {}", e.getMessage()));
    }

    // ── Cycle de vie de la session (rafraîchissement, révocation) ─────────────────────

    /**
     * Échange un refresh token contre une nouvelle paire de jetons. KernelCore fait tourner
     * le refresh token à chaque échange : le client doit remplacer les deux, pas seulement
     * l'access token. Un refresh invalide ou expiré remonte en 401.
     */
    public Mono<KernelRefreshTokenResponseDto> refresh(String refreshToken) {
        return kernelCoreWebClient.post()
                .uri("/api/auth/refresh")
                .bodyValue(new KernelRefreshTokenRequestDto(refreshToken))
                .retrieve()
                .onStatus(HttpStatusCode::is4xxClientError,
                        resp -> Mono.error(new SessionExpiredException(
                                "Session expirée, reconnectez-vous")))
                .onStatus(HttpStatusCode::is5xxServerError,
                        resp -> Mono.error(new KernelCoreUnavailableException(
                                "KernelCore indisponible pour le rafraîchissement de session")))
                .bodyToMono(REFRESH_TYPE)
                .flatMap(response -> unwrap(response, "/api/auth/refresh", SessionExpiredException::new))
                .doOnError(e -> log.debug("Échec rafraîchissement de session KernelCore: {}", e.getMessage()));
    }

    /**
     * Révoque la session côté KernelCore. Best-effort : une déconnexion ne doit jamais
     * échouer côté portail — le client purge ses jetons quoi qu'il arrive, l'appelant
     * transforme donc l'erreur en succès silencieux.
     */
    public Mono<Void> logout(String accessToken, String refreshToken) {
        return kernelCoreWebClient.post()
                .uri("/api/auth/logout")
                .header("Authorization", "Bearer " + accessToken)
                .bodyValue(new KernelRefreshTokenRequestDto(refreshToken))
                .retrieve()
                .bodyToMono(VOID_TYPE)
                .then()
                .doOnError(e -> log.debug("Révocation de session KernelCore sans effet: {}", e.getMessage()));
    }

    /**
     * Déballe une ApiResponse KernelCore, en laissant l'appelant choisir l'erreur métier
     * levée quand le corps est en échec (les codes d'erreur diffèrent selon le parcours).
     */
    private <T> Mono<T> unwrap(KernelApiResponse<T> response, String endpoint,
                               java.util.function.Function<String, RuntimeException> onFailure) {
        if (!response.isSuccess() || response.getData() == null) {
            String message = response.getMessage() != null && !response.getMessage().isBlank()
                    ? response.getMessage()
                    : "réponse KernelCore invalide pour " + endpoint;
            return Mono.error(onFailure.apply(message));
        }
        return Mono.just(response.getData());
    }

    /** Login direct (jeton présent) ou défi MFA (202 : code envoyé, mfaToken à confirmer). */
    private Mono<KernelLoginResultDto> unwrapLogin(KernelApiResponse<KernelLoginResponseDto> response) {
        if (response.getData() == null) {
            return Mono.error(new KernelCoreUnavailableException("Réponse KernelCore invalide pour /api/auth/login"));
        }
        KernelLoginResponseDto data = response.getData();
        String token = data.resolveAccessToken();
        if (token != null && !token.isBlank()) {
            return Mono.just(KernelLoginResultDto.authenticated(token, safeOrganizations(data),
                    KernelLoginResultDto.Session.from(data)));
        }
        String mfaToken = data.resolveMfaToken();
        if (mfaToken != null && !mfaToken.isBlank()) {
            return Mono.just(KernelLoginResultDto.mfaChallenge(mfaToken, data.resolveMfaChannel()));
        }
        return Mono.error(new KernelCoreUnavailableException(
                "Réponse KernelCore sans jeton d'accès ni défi MFA pour /api/auth/login"));
    }

    /** Réponse de mfa/confirm : le jeton doit être présent, pas de nouveau défi possible. */
    private Mono<KernelLoginResultDto> unwrapAuthenticated(KernelApiResponse<KernelLoginResponseDto> response) {
        if (!response.isSuccess() || response.getData() == null) {
            return Mono.error(new KernelCoreUnavailableException("Réponse KernelCore invalide pour /api/auth/login/mfa/confirm"));
        }
        String token = response.getData().resolveAccessToken();
        if (token == null || token.isBlank()) {
            return Mono.error(new KernelCoreUnavailableException("Réponse KernelCore sans jeton d'accès après confirmation MFA"));
        }
        return Mono.just(KernelLoginResultDto.authenticated(token, safeOrganizations(response.getData()),
                KernelLoginResultDto.Session.from(response.getData())));
    }

    /**
     * Kernel Core rend ses erreurs en enveloppe JSON. Concaténer le corps brut dans le message
     * remonté au client affichait à l'utilisateur final
     * {@code Inscription refusée: {"success":false,"data":null,"message":"A user already exists…"}} :
     * la seule information exploitable y était noyée. On n'en garde que {@code message}, et on
     * retombe sur le corps brut quand ce n'est pas du JSON (page d'erreur d'un proxy, corps vide).
     */
    static String explain(String body) {
        if (body == null || body.isBlank()) {
            return "réponse vide";
        }
        try {
            JsonNode node = ERROR_MAPPER.readTree(body);
            JsonNode message = node.get("message");
            if (message != null && message.isTextual() && !message.asText().isBlank()) {
                return message.asText();
            }
        } catch (Exception e) {
            // Corps non JSON : on rend le texte tel quel, tronqué.
        }
        return body.length() > 300 ? body.substring(0, 300) + "…" : body;
    }

    private static List<KernelOrganizationSummaryDto> safeOrganizations(KernelLoginResponseDto data) {
        return data.getOrganizations() != null ? data.getOrganizations() : List.of();
    }
}
