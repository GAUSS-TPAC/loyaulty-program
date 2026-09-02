package com.yowyob.loyalty.infrastructure.kernelcore.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Réponse de POST /api/auth/refresh. KernelCore fait tourner le refresh token à chaque
 * échange : le client doit remplacer les deux jetons, pas seulement l'access token.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class KernelRefreshTokenResponseDto {

    private String accessToken;
    private String refreshToken;
    private String tokenType;
    private Integer accessExpiresInSeconds;
    private Integer refreshExpiresInSeconds;

    public String getAccessToken() { return accessToken; }
    public void setAccessToken(String accessToken) { this.accessToken = accessToken; }
    public String getRefreshToken() { return refreshToken; }
    public void setRefreshToken(String refreshToken) { this.refreshToken = refreshToken; }
    public String getTokenType() { return tokenType; }
    public void setTokenType(String tokenType) { this.tokenType = tokenType; }
    public Integer getAccessExpiresInSeconds() { return accessExpiresInSeconds; }
    public void setAccessExpiresInSeconds(Integer v) { this.accessExpiresInSeconds = v; }
    public Integer getRefreshExpiresInSeconds() { return refreshExpiresInSeconds; }
    public void setRefreshExpiresInSeconds(Integer v) { this.refreshExpiresInSeconds = v; }
}
