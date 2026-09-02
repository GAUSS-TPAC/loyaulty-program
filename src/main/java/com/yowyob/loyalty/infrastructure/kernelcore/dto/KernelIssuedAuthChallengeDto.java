package com.yowyob.loyalty.infrastructure.kernelcore.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Défi émis par KernelCore (reset de mot de passe, vérification email…).
 *
 * {@code deliveryMode} vaut EMAIL quand un provider SMTP est configuré, et PREVIEW_ONLY
 * sinon : dans ce dernier cas aucun email n'est parti et {@code challengeTokenPreview}
 * porte le jeton en clair. On ne le remonte jamais au client — c'est un credential.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class KernelIssuedAuthChallengeDto {

    private String deliveryMode;
    private String challengeTokenPreview;
    private Integer expiresInSeconds;

    public String getDeliveryMode() { return deliveryMode; }
    public void setDeliveryMode(String deliveryMode) { this.deliveryMode = deliveryMode; }
    public String getChallengeTokenPreview() { return challengeTokenPreview; }
    public void setChallengeTokenPreview(String challengeTokenPreview) { this.challengeTokenPreview = challengeTokenPreview; }
    public Integer getExpiresInSeconds() { return expiresInSeconds; }
    public void setExpiresInSeconds(Integer expiresInSeconds) { this.expiresInSeconds = expiresInSeconds; }
}
