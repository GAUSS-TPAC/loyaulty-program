package com.yowyob.loyalty.api.bonification.dto;

import jakarta.validation.constraints.NotBlank;

/** Identifiants de l'API Bonification propres au tenant. Le mot de passe n'est jamais relu. */
public record BonificationCredentialsRequest(@NotBlank String username, @NotBlank String password) {}
