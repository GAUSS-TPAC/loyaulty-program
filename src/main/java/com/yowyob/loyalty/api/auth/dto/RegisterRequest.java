package com.yowyob.loyalty.api.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * La règle de mot de passe reprend celle d'auth-core ("password must contain at least 10
 * characters, upper and lower case letters, a digit and a symbol") : la valider ici évite
 * de laisser l'utilisateur découvrir le refus en anglais après l'aller-retour KernelCore.
 */
public record RegisterRequest(
        @NotBlank String firstName,
        @NotBlank String lastName,
        @NotBlank @Email String email,
        @NotBlank
        @Size(min = 10, message = "Le mot de passe doit contenir au moins 10 caractères")
        @Pattern(
                regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[^A-Za-z0-9]).+$",
                message = "Le mot de passe doit contenir une minuscule, une majuscule, un chiffre et un symbole")
        String password
) {}
