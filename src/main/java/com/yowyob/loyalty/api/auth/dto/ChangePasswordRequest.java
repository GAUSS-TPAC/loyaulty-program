package com.yowyob.loyalty.api.auth.dto;

import jakarta.validation.constraints.NotBlank;

/** Changement de mot de passe par l'utilisateur connecté : le mot de passe actuel fait foi. */
public record ChangePasswordRequest(@NotBlank String currentPassword, @StrongPassword String newPassword) {}
