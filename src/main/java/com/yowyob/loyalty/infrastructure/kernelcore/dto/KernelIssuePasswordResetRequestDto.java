package com.yowyob.loyalty.infrastructure.kernelcore.dto;

/** Corps de POST /api/auth/password-reset/issue : le jeton de sélection et le compte visé. */
public record KernelIssuePasswordResetRequestDto(String selectionToken, String contextId) {}
