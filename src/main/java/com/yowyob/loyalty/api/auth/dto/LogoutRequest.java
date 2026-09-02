package com.yowyob.loyalty.api.auth.dto;

/**
 * @param refreshToken optionnel : une déconnexion sans refresh token (session déjà purgée
 *                     côté navigateur) reste une déconnexion valide.
 */
public record LogoutRequest(String refreshToken) {}
