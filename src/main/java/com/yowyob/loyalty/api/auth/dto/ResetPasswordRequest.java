package com.yowyob.loyalty.api.auth.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * @param token jeton de réinitialisation porté par le lien reçu par email (signé et
 *              de courte durée côté KernelCore).
 */
public record ResetPasswordRequest(@NotBlank String token, @StrongPassword String newPassword) {}
