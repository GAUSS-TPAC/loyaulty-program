package com.yowyob.loyalty.api.auth.dto;

import jakarta.validation.constraints.NotBlank;

/** @param token jeton de vérification porté par le lien reçu par email. */
public record ConfirmEmailRequest(@NotBlank String token) {}
