package com.yowyob.loyalty.api.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/** Formulaire « mot de passe oublié » : l'adresse email du compte à réinitialiser. */
public record ForgotPasswordRequest(@NotBlank @Email String email) {}
