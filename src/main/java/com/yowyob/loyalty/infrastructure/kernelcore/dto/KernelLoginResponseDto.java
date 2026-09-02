package com.yowyob.loyalty.infrastructure.kernelcore.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * Réponse de POST /api/auth/login sur KernelCore (auth-core).
 * Le nom exact du champ porteur du JWT n'est pas fixé par une spec partagée dans ce
 * dépôt (pas de collection Postman jointe) : on tolère plusieurs conventions usuelles.
 *
 * Le JWT retourné (tenant-scoped) ne porte pas de claim d'organisation ({@code oid}) :
 * l'organisation cible doit être choisie parmi {@code organizations} (une entrée par
 * membership de l'acteur) et propagée par le client via le header {@code X-Organization-Id}
 * sur les appels suivants — voir KernelCoreAuthAdapter et le contrat documenté dans
 * docs/kernel_core documentation.md ("Contrat minimal backend vers kernel").
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class KernelLoginResponseDto {

    private String accessToken;
    private String token;
    private String jwt;
    private List<KernelOrganizationSummaryDto> organizations;

    // Défi MFA/OTP (réponse 202 de /api/auth/login quand le compte a le MFA actif :
    // pas de jeton, un code est envoyé par email et doit être confirmé via
    // POST /api/auth/login/mfa/confirm {mfaToken, code}). Le jeton de défi arrive
    // selon la version sous "challengeToken" (OtpChallengeResponse), "mfaToken" ou
    // "sessionToken" (LoginResponse) — on tolère les trois.
    private String challengeToken;
    private String mfaToken;
    private String sessionToken;
    private String deliveryMode;
    private String mfaChannel;
    /** Nom réellement employé par auth-core dans la réponse 202 « MFA required » (vérifié en appel réel). */
    private String channel;

    // Durée de vie de la session émise par KernelCore. Sans ces champs le portail ne peut
    // ni anticiper l'expiration ni rafraîchir : il découvre la fin de session sur un 401.
    private Integer expiresInSeconds;
    private String refreshToken;
    private Integer refreshExpiresInSeconds;
    private boolean emailVerified;
    private boolean mfaEnabled;

    public String resolveAccessToken() {
        if (accessToken != null && !accessToken.isBlank()) return accessToken;
        if (token != null && !token.isBlank()) return token;
        return jwt;
    }

    public String resolveMfaToken() {
        if (mfaToken != null && !mfaToken.isBlank()) return mfaToken;
        if (challengeToken != null && !challengeToken.isBlank()) return challengeToken;
        return sessionToken;
    }

    public String resolveMfaChannel() {
        if (mfaChannel != null && !mfaChannel.isBlank()) return mfaChannel;
        if (channel != null && !channel.isBlank()) return channel;
        return deliveryMode;
    }

    public String getAccessToken() { return accessToken; }
    public void setAccessToken(String accessToken) { this.accessToken = accessToken; }
    public String getToken() { return token; }
    public void setToken(String token) { this.token = token; }
    public String getJwt() { return jwt; }
    public void setJwt(String jwt) { this.jwt = jwt; }
    public List<KernelOrganizationSummaryDto> getOrganizations() { return organizations; }
    public void setOrganizations(List<KernelOrganizationSummaryDto> organizations) { this.organizations = organizations; }
    public String getChallengeToken() { return challengeToken; }
    public void setChallengeToken(String challengeToken) { this.challengeToken = challengeToken; }
    public String getMfaToken() { return mfaToken; }
    public void setMfaToken(String mfaToken) { this.mfaToken = mfaToken; }
    public String getSessionToken() { return sessionToken; }
    public void setSessionToken(String sessionToken) { this.sessionToken = sessionToken; }
    public String getDeliveryMode() { return deliveryMode; }
    public void setDeliveryMode(String deliveryMode) { this.deliveryMode = deliveryMode; }
    public String getMfaChannel() { return mfaChannel; }
    public void setMfaChannel(String mfaChannel) { this.mfaChannel = mfaChannel; }
    public String getChannel() { return channel; }
    public void setChannel(String channel) { this.channel = channel; }
    public Integer getExpiresInSeconds() { return expiresInSeconds; }
    public void setExpiresInSeconds(Integer expiresInSeconds) { this.expiresInSeconds = expiresInSeconds; }
    public String getRefreshToken() { return refreshToken; }
    public void setRefreshToken(String refreshToken) { this.refreshToken = refreshToken; }
    public Integer getRefreshExpiresInSeconds() { return refreshExpiresInSeconds; }
    public void setRefreshExpiresInSeconds(Integer v) { this.refreshExpiresInSeconds = v; }
    public boolean isEmailVerified() { return emailVerified; }
    public void setEmailVerified(boolean emailVerified) { this.emailVerified = emailVerified; }
    public boolean isMfaEnabled() { return mfaEnabled; }
    public void setMfaEnabled(boolean mfaEnabled) { this.mfaEnabled = mfaEnabled; }
}
