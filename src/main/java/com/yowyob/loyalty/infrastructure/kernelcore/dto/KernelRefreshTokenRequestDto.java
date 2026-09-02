package com.yowyob.loyalty.infrastructure.kernelcore.dto;

/** Corps de POST /api/auth/refresh et de POST /api/auth/logout. */
public record KernelRefreshTokenRequestDto(String refreshToken) {}
