package com.yowyob.loyalty.infrastructure.kernelcore.dto;

/** Corps de POST /api/auth/change-password (utilisateur connecté, Bearer requis). */
public record KernelChangePasswordRequestDto(String currentPassword, String newPassword) {}
