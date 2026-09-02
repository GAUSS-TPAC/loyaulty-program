package com.yowyob.loyalty.infrastructure.kernelcore.dto;

/** Corps de POST /api/auth/reset-password : le jeton reçu par email et le nouveau mot de passe. */
public record KernelResetPasswordRequestDto(String resetToken, String newPassword) {}
