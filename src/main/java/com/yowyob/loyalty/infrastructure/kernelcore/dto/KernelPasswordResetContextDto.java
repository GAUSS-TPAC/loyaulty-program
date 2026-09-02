package com.yowyob.loyalty.infrastructure.kernelcore.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Un compte candidat à la réinitialisation, renvoyé par POST /api/auth/forgot-password.
 * Un même principal (email) peut exister dans plusieurs tenants KernelCore : le contextId
 * désigne celui à réinitialiser et doit être renvoyé à /api/auth/password-reset/issue.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class KernelPasswordResetContextDto {

    private String contextId;
    private String tenantId;
    private String userId;
    private String actorId;
    private String username;
    private String email;

    public String getContextId() { return contextId; }
    public void setContextId(String contextId) { this.contextId = contextId; }
    public String getTenantId() { return tenantId; }
    public void setTenantId(String tenantId) { this.tenantId = tenantId; }
    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }
    public String getActorId() { return actorId; }
    public void setActorId(String actorId) { this.actorId = actorId; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
}
