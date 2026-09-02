package com.yowyob.loyalty.api.auth.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.yowyob.loyalty.application.auth.AuthService;
import com.yowyob.loyalty.infrastructure.kernelcore.dto.KernelLoginResultDto;

/**
 * Réponse de login en deux formes possibles :
 * - authentifié : {@code token} + organisation active (organizationId à renvoyer par le client
 *   dans le header X-Organization-Id — le JWT KernelCore ne porte pas de claim d'organisation) ;
 * - défi MFA : {@code mfaRequired=true} + {@code mfaToken} — un code a été envoyé par email,
 *   à confirmer via POST /api/v1/auth/login/mfa.
 *
 * Le cas authentifié porte aussi la durée de vie de la session et le refresh token, pour que
 * le client rafraîchisse avant expiration au lieu de découvrir la fin de session sur un 401.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record LoginResponse(
        String token,
        String organizationId,
        String organizationCode,
        String organizationName,
        Boolean mfaRequired,
        String mfaToken,
        String mfaChannel,
        String refreshToken,
        Integer expiresInSeconds,
        Integer refreshExpiresInSeconds,
        Boolean emailVerified
) {
    public LoginResponse(AuthService.AuthResult result) {
        this(result.token(), result.organizationId(), result.organizationCode(), result.organizationName(),
                null, null, null,
                session(result).refreshToken(), session(result).expiresInSeconds(),
                session(result).refreshExpiresInSeconds(), session(result).emailVerified());
    }

    private static KernelLoginResultDto.Session session(AuthService.AuthResult result) {
        return result.session() != null ? result.session() : KernelLoginResultDto.Session.UNKNOWN;
    }

    public static LoginResponse from(AuthService.LoginOutcome outcome) {
        if (outcome.isMfaRequired()) {
            return new LoginResponse(null, null, null, null, true, outcome.mfaToken(), outcome.mfaChannel(),
                    null, null, null, null);
        }
        return new LoginResponse(outcome.result());
    }
}
