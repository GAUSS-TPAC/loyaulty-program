package com.yowyob.loyalty.api.auth.dto;

import com.yowyob.loyalty.infrastructure.kernelcore.dto.KernelRefreshTokenResponseDto;

/**
 * Nouvelle paire de jetons après rafraîchissement. Le client doit remplacer les deux :
 * KernelCore révoque l'ancien refresh token à chaque échange (rotation).
 */
public record SessionResponse(String token, String refreshToken, Integer expiresInSeconds,
                              Integer refreshExpiresInSeconds) {

    public SessionResponse(KernelRefreshTokenResponseDto dto) {
        this(dto.getAccessToken(), dto.getRefreshToken(), dto.getAccessExpiresInSeconds(),
                dto.getRefreshExpiresInSeconds());
    }
}
