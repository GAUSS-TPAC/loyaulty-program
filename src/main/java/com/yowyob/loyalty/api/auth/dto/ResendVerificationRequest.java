package com.yowyob.loyalty.api.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/** Renvoi du mail de vérification : appelé sans session, un compte non vérifié ne peut pas se connecter. */
public record ResendVerificationRequest(@NotBlank @Email String email) {}
