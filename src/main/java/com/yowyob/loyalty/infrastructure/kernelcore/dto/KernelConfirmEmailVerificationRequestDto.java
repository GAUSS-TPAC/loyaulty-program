package com.yowyob.loyalty.infrastructure.kernelcore.dto;

/** Corps de POST /api/auth/email-verification/confirm. */
public record KernelConfirmEmailVerificationRequestDto(String verificationToken) {}
