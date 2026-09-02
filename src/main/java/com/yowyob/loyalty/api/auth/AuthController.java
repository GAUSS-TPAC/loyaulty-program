package com.yowyob.loyalty.api.auth;

import com.yowyob.loyalty.api.auth.dto.AccountStatusResponse;
import com.yowyob.loyalty.api.auth.dto.ChallengeAcceptedResponse;
import com.yowyob.loyalty.api.auth.dto.ChangePasswordRequest;
import com.yowyob.loyalty.api.auth.dto.ConfirmEmailRequest;
import com.yowyob.loyalty.api.auth.dto.ConfirmMfaRequest;
import com.yowyob.loyalty.api.auth.dto.ForgotPasswordRequest;
import com.yowyob.loyalty.api.auth.dto.LoginRequest;
import com.yowyob.loyalty.api.auth.dto.LoginResponse;
import com.yowyob.loyalty.api.auth.dto.LogoutRequest;
import com.yowyob.loyalty.api.auth.dto.RefreshTokenRequest;
import com.yowyob.loyalty.api.auth.dto.RegisterRequest;
import com.yowyob.loyalty.api.auth.dto.RegisterResponse;
import com.yowyob.loyalty.api.auth.dto.ResendVerificationRequest;
import com.yowyob.loyalty.api.auth.dto.ResetPasswordRequest;
import com.yowyob.loyalty.api.auth.dto.SessionResponse;
import com.yowyob.loyalty.application.auth.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Auth", description = "Authentification des administrateurs de tenant (portail admin)")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    @Operation(summary = "Connexion admin", description = "Authentifie un administrateur par email/mot de passe via KernelCore. "
            + "Si le compte a le MFA actif, retourne mfaRequired=true + mfaToken (code envoyé par email, "
            + "à confirmer via /login/mfa) ; sinon retourne directement le JWT et l'organisation active "
            + "(à renvoyer via X-Organization-Id).")
    public Mono<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request.email(), request.password(), request.organizationId())
                .map(LoginResponse::from);
    }

    @PostMapping("/login/mfa")
    @Operation(summary = "Confirmation MFA", description = "Deuxième étape du login : confirme le code reçu par email "
            + "avec le mfaToken renvoyé par /login, et retourne le JWT ainsi que l'organisation active.")
    public Mono<LoginResponse> confirmMfa(@Valid @RequestBody ConfirmMfaRequest request) {
        return authService.confirmMfa(request.mfaToken(), request.code(), request.organizationId())
                .map(LoginResponse::new);
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Inscription admin", description = "Crée un compte KernelCore pour l'organisation de ce déploiement. Le compte reste EMAIL_VERIFICATION_REQUIRED jusqu'à confirmation de l'email (le login échouera avant ça).")
    public Mono<RegisterResponse> register(@Valid @RequestBody RegisterRequest request) {
        return authService.register(request.firstName(), request.lastName(), request.email(), request.password())
                .map(RegisterResponse::new);
    }

    // ── Mot de passe ─────────────────────────────────────────────────────────────────

    @PostMapping("/password/forgot")
    @ResponseStatus(HttpStatus.ACCEPTED)
    @Operation(summary = "Mot de passe oublié", description = "Envoie un lien de réinitialisation à l'adresse "
            + "indiquée. La réponse est identique que le compte existe ou non, pour ne pas permettre "
            + "d'énumérer les adresses inscrites.")
    public Mono<ChallengeAcceptedResponse> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        return authService.forgotPassword(request.email())
                .thenReturn(ChallengeAcceptedResponse.SENT_IF_ACCOUNT_EXISTS);
    }

    @PostMapping("/password/reset")
    @Operation(summary = "Réinitialisation du mot de passe", description = "Consomme le jeton porté par le lien "
            + "reçu par email et fixe le nouveau mot de passe. Le jeton est à usage unique et de courte durée.")
    public Mono<AccountStatusResponse> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        return authService.resetPassword(request.token(), request.newPassword())
                .map(AccountStatusResponse::new);
    }

    @PostMapping("/password/change")
    @Operation(summary = "Changement du mot de passe", description = "Pour l'utilisateur connecté : exige le mot "
            + "de passe actuel. Les sessions existantes ne sont pas révoquées par KernelCore.")
    public Mono<AccountStatusResponse> changePassword(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @Valid @RequestBody ChangePasswordRequest request) {
        return authService.changePassword(bearer(authorization), request.currentPassword(), request.newPassword())
                .map(AccountStatusResponse::new);
    }

    // ── Vérification de l'adresse email ──────────────────────────────────────────────

    @PostMapping("/email/resend")
    @ResponseStatus(HttpStatus.ACCEPTED)
    @Operation(summary = "Renvoyer la vérification d'email", description = "Appelé sans session : un compte dont "
            + "l'adresse n'est pas confirmée ne peut pas se connecter. Réponse neutre, comme /password/forgot.")
    public Mono<ChallengeAcceptedResponse> resendVerification(@Valid @RequestBody ResendVerificationRequest request) {
        return authService.resendEmailVerification(request.email())
                .thenReturn(ChallengeAcceptedResponse.SENT_IF_ACCOUNT_EXISTS);
    }

    @PostMapping("/email/confirm")
    @Operation(summary = "Confirmer l'adresse email", description = "Consomme le jeton porté par le lien de "
            + "vérification ; le compte devient alors utilisable pour se connecter.")
    public Mono<AccountStatusResponse> confirmEmail(@Valid @RequestBody ConfirmEmailRequest request) {
        return authService.confirmEmailVerification(request.token())
                .map(AccountStatusResponse::new);
    }

    // ── Session ──────────────────────────────────────────────────────────────────────

    @PostMapping("/refresh")
    @Operation(summary = "Rafraîchir la session", description = "Échange le refresh token contre une nouvelle "
            + "paire de jetons. KernelCore révoque l'ancien refresh token : le client doit remplacer les deux.")
    public Mono<SessionResponse> refresh(@Valid @RequestBody RefreshTokenRequest request) {
        return authService.refresh(request.refreshToken()).map(SessionResponse::new);
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Déconnexion", description = "Révoque la session côté KernelCore. Best-effort : la "
            + "réponse est un succès même si la révocation échoue, le client purge ses jetons dans tous les cas.")
    public Mono<Void> logout(@RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
                             @RequestBody(required = false) LogoutRequest request) {
        return authService.logout(bearer(authorization), request != null ? request.refreshToken() : null);
    }

    private static String bearer(String authorizationHeader) {
        return authorizationHeader != null && authorizationHeader.regionMatches(true, 0, "Bearer ", 0, 7)
                ? authorizationHeader.substring(7).trim()
                : authorizationHeader;
    }
}
