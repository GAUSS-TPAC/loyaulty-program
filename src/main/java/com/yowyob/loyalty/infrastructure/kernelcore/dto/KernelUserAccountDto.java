package com.yowyob.loyalty.infrastructure.kernelcore.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.UUID;

/**
 * UserAccountResponse de Kernel Core, renvoyé par POST /api/auth/register ainsi que par les
 * endpoints de credentials (reset-password, change-password, email-verification/confirm).
 * Seul l'état exploité par le portail est mappé.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class KernelUserAccountDto {

    private UUID id;
    private String email;
    private String username;
    private String status;
    private boolean emailVerified;
    private boolean mfaEnabled;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public boolean isEmailVerified() { return emailVerified; }
    public void setEmailVerified(boolean emailVerified) { this.emailVerified = emailVerified; }
    public boolean isMfaEnabled() { return mfaEnabled; }
    public void setMfaEnabled(boolean mfaEnabled) { this.mfaEnabled = mfaEnabled; }
}
