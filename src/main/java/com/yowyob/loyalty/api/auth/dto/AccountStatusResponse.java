package com.yowyob.loyalty.api.auth.dto;

import com.yowyob.loyalty.infrastructure.kernelcore.dto.KernelUserAccountDto;

/** État du compte après une opération sur ses credentials (reset, changement, vérification). */
public record AccountStatusResponse(String email, String status, boolean emailVerified, boolean mfaEnabled) {

    public AccountStatusResponse(KernelUserAccountDto account) {
        this(account.getEmail(), account.getStatus(), account.isEmailVerified(), account.isMfaEnabled());
    }
}
