package com.yowyob.loyalty.api.auth.dto;

import jakarta.validation.constraints.NotBlank;

/** Rafraîchissement de session : KernelCore fait tourner le refresh token à chaque échange. */
public record RefreshTokenRequest(@NotBlank String refreshToken) {}
