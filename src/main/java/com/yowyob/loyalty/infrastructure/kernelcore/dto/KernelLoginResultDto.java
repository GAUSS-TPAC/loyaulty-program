package com.yowyob.loyalty.infrastructure.kernelcore.dto;

import java.util.List;

/**
 * Résultat exploitable d'un appel login KernelCore : soit un JWT d'accès et la liste des
 * organisations accessibles (login direct), soit un défi MFA à confirmer via
 * POST /api/auth/login/mfa/confirm (mfaToken + canal d'envoi du code) — voir
 * KernelLoginResponseDto.
 */
public record KernelLoginResultDto(
        String accessToken,
        List<KernelOrganizationSummaryDto> organizations,
        String mfaToken,
        String mfaChannel,
        Session session
) {

    /**
     * Durée de vie de la session émise par KernelCore, à propager au client pour qu'il
     * rafraîchisse avant expiration au lieu de découvrir la fin de session sur un 401.
     *
     * @param emailVerified {@code null} quand KernelCore ne l'a pas renvoyé : « inconnu »
     *                      et « non vérifié » ne doivent pas être confondus, sans quoi le
     *                      portail réclamerait une vérification déjà faite.
     */
    public record Session(String refreshToken, Integer expiresInSeconds, Integer refreshExpiresInSeconds,
                          Boolean emailVerified) {

        public static final Session UNKNOWN = new Session(null, null, null, null);

        public static Session from(KernelLoginResponseDto data) {
            return new Session(data.getRefreshToken(), data.getExpiresInSeconds(),
                    data.getRefreshExpiresInSeconds(), data.isEmailVerified());
        }
    }

    public static KernelLoginResultDto authenticated(String accessToken, List<KernelOrganizationSummaryDto> organizations) {
        return authenticated(accessToken, organizations, Session.UNKNOWN);
    }

    public static KernelLoginResultDto authenticated(String accessToken, List<KernelOrganizationSummaryDto> organizations,
                                                     Session session) {
        return new KernelLoginResultDto(accessToken, organizations, null, null, session);
    }

    public static KernelLoginResultDto mfaChallenge(String mfaToken, String mfaChannel) {
        return new KernelLoginResultDto(null, List.of(), mfaToken, mfaChannel, Session.UNKNOWN);
    }

    public boolean mfaRequired() {
        return accessToken == null || accessToken.isBlank();
    }
}
