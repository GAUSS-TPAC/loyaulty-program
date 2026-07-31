package com.yowyob.loyalty.infrastructure.kernelcore.dto;

/**
 * Corps de requête pour POST /api/auth/sign-up (inscription publique KernelCore).
 *
 * <p>Le schéma {@code PublicSignUpRequest} d'auth-core exige {@code email}, {@code firstName},
 * {@code lastName} et {@code username} ; il nomme le jeton {@code signUpSelectionToken} — et non
 * {@code selectionToken} comme dans la réponse de discover-sign-up-contexts — et le refuse sans le
 * {@code contextId} correspondant ("contextId is required when signUpSelectionToken is provided").
 * Les deux valeurs viennent de POST /api/auth/discover-sign-up-contexts.
 */
public record KernelPublicSignUpRequestDto(
        String signUpSelectionToken,
        String contextId,
        String username,
        String firstName,
        String lastName,
        String email,
        String password
) {}
