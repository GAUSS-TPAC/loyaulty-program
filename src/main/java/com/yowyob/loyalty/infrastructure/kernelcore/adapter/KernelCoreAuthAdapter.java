package com.yowyob.loyalty.infrastructure.kernelcore.adapter;

import com.yowyob.loyalty.infrastructure.kernelcore.dto.KernelApiResponse;
import com.yowyob.loyalty.infrastructure.kernelcore.dto.KernelConfirmMfaLoginRequestDto;
import com.yowyob.loyalty.infrastructure.kernelcore.dto.KernelDiscoverContextsResponseDto;
import com.yowyob.loyalty.infrastructure.kernelcore.dto.KernelDiscoverSignUpContextsRequestDto;
import com.yowyob.loyalty.infrastructure.kernelcore.dto.KernelDiscoverSignUpContextsResponseDto;
import com.yowyob.loyalty.infrastructure.kernelcore.dto.KernelDiscoveredContextDto;
import com.yowyob.loyalty.infrastructure.kernelcore.dto.KernelLoginRequestDto;
import com.yowyob.loyalty.infrastructure.kernelcore.dto.KernelLoginResponseDto;
import com.yowyob.loyalty.infrastructure.kernelcore.dto.KernelLoginResultDto;
import com.yowyob.loyalty.infrastructure.kernelcore.dto.KernelOrganizationSummaryDto;
import com.yowyob.loyalty.infrastructure.kernelcore.dto.KernelPublicSignUpRequestDto;
import com.yowyob.loyalty.infrastructure.kernelcore.dto.KernelSignUpResultDto;
import com.yowyob.loyalty.shared.exception.InvalidCredentialsException;
import com.yowyob.loyalty.shared.exception.KernelCoreUnavailableException;
import com.yowyob.loyalty.shared.exception.RegistrationFailedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatusCode;
import org.springframework.web.reactive.function.client.WebClient;
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

    private static final ParameterizedTypeReference<KernelApiResponse<KernelLoginResponseDto>> LOGIN_TYPE =
            new ParameterizedTypeReference<>() {};

    private static final ParameterizedTypeReference<KernelApiResponse<KernelDiscoverContextsResponseDto>> DISCOVER_TYPE =
            new ParameterizedTypeReference<>() {};

    private static final ParameterizedTypeReference<KernelApiResponse<KernelDiscoverSignUpContextsResponseDto>> DISCOVER_SIGNUP_TYPE =
            new ParameterizedTypeReference<>() {};

    private static final ParameterizedTypeReference<KernelApiResponse<KernelSignUpResultDto>> SIGNUP_TYPE =
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
                                .flatMap(body -> Mono.error(new InvalidCredentialsException("Authentification refusée: " + body))))
                .onStatus(HttpStatusCode::is5xxServerError,
                        resp -> Mono.error(new KernelCoreUnavailableException("KernelCore indisponible pour l'authentification")))
                .bodyToMono(LOGIN_TYPE)
                .flatMap(this::unwrapLogin)
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
                                .flatMap(body -> Mono.error(new InvalidCredentialsException("Découverte des contextes refusée: " + body))))
                .onStatus(HttpStatusCode::is5xxServerError,
                        resp -> Mono.error(new KernelCoreUnavailableException("KernelCore indisponible pour la découverte des contextes")))
                .bodyToMono(DISCOVER_TYPE)
                .flatMap(this::unwrapContexts)
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
                                        "Découverte du contexte d'inscription refusée: " + body))))
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
                                        "Inscription refusée: " + body))))
                .onStatus(HttpStatusCode::is5xxServerError,
                        resp -> Mono.error(new KernelCoreUnavailableException("KernelCore indisponible pour l'inscription")))
                .bodyToMono(SIGNUP_TYPE)
                .flatMap(response -> {
                    if (!response.isSuccess() || response.getData() == null) {
                        String message = response.getMessage() != null ? response.getMessage() : "réponse KernelCore invalide";
                        return Mono.<KernelSignUpResultDto>error(new RegistrationFailedException(message));
                    }
                    return Mono.just(response.getData());
                })
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
                                .flatMap(body -> Mono.error(new InvalidCredentialsException("Vérification refusée: " + body))))
                .onStatus(HttpStatusCode::is5xxServerError,
                        resp -> Mono.error(new KernelCoreUnavailableException("KernelCore indisponible pour la vérification MFA")))
                .bodyToMono(LOGIN_TYPE)
                .flatMap(this::unwrapAuthenticated)
                .doOnError(e -> log.warn("Échec confirmation MFA KernelCore: {}", e.getMessage()));
    }

    /** Login direct (jeton présent) ou défi MFA (202 : code envoyé, mfaToken à confirmer). */
    private Mono<KernelLoginResultDto> unwrapLogin(KernelApiResponse<KernelLoginResponseDto> response) {
        if (response.getData() == null) {
            return Mono.error(new KernelCoreUnavailableException("Réponse KernelCore invalide pour /api/auth/login"));
        }
        KernelLoginResponseDto data = response.getData();
        String token = data.resolveAccessToken();
        if (token != null && !token.isBlank()) {
            return Mono.just(KernelLoginResultDto.authenticated(token, safeOrganizations(data)));
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
        return Mono.just(KernelLoginResultDto.authenticated(token, safeOrganizations(response.getData())));
    }

    private static List<KernelOrganizationSummaryDto> safeOrganizations(KernelLoginResponseDto data) {
        return data.getOrganizations() != null ? data.getOrganizations() : List.of();
    }
}
