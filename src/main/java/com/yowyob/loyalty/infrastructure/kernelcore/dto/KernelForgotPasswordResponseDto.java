package com.yowyob.loyalty.infrastructure.kernelcore.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * Réponse de POST /api/auth/forgot-password : jeton court de sélection + comptes
 * correspondant au principal. Le couple {selectionToken, contextId} alimente ensuite
 * POST /api/auth/password-reset/issue, qui envoie réellement l'email de réinitialisation.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class KernelForgotPasswordResponseDto {

    private String principal;
    private Integer matchingAccountCount;
    private String selectionToken;
    private Integer selectionTokenExpiresInSeconds;
    private List<KernelPasswordResetContextDto> contexts;

    public String getPrincipal() { return principal; }
    public void setPrincipal(String principal) { this.principal = principal; }
    public Integer getMatchingAccountCount() { return matchingAccountCount; }
    public void setMatchingAccountCount(Integer matchingAccountCount) { this.matchingAccountCount = matchingAccountCount; }
    public String getSelectionToken() { return selectionToken; }
    public void setSelectionToken(String selectionToken) { this.selectionToken = selectionToken; }
    public Integer getSelectionTokenExpiresInSeconds() { return selectionTokenExpiresInSeconds; }
    public void setSelectionTokenExpiresInSeconds(Integer v) { this.selectionTokenExpiresInSeconds = v; }
    public List<KernelPasswordResetContextDto> getContexts() { return contexts; }
    public void setContexts(List<KernelPasswordResetContextDto> contexts) { this.contexts = contexts; }
}
