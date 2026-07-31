package com.yowyob.loyalty.infrastructure.kernelcore.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.math.BigDecimal;

/**
 * Corps de POST /api/payments/orders (Kernel Core InitiatePaymentRequest).
 *
 * @param clientId       identité de la ClientApplication appelante (même valeur que X-Client-Id)
 * @param serviceCode    PlatformServiceCode facturé pour cet encaissement
 * @param provider       MYCOOLPAY | STRIPE
 * @param method         MOBILE_MONEY | CARD
 * @param payerReference numéro Mobile Money (E.164) ou référence carte du payeur
 * @param callbackUrl    URL que Kernel Core rappelle en fin de parcours
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record KernelInitiatePaymentRequest(
    String clientId,
    String serviceCode,
    String idempotencyKey,
    BigDecimal amount,
    String currency,
    String provider,
    String method,
    String payerReference,
    String description,
    String callbackUrl
) {}
