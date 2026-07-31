package com.yowyob.loyalty.domain.wallet.model;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Vue d'un ordre de paiement tel que la passerelle le connaît.
 *
 * <p>{@code rawStatus} conserve le libellé brut renvoyé par la passerelle : le contrat
 * OpenAPI de Kernel Core type {@code PaymentOrderResponse.status} en {@code string} libre
 * (aucune énumération publiée), donc {@link #status} est une interprétation — la valeur
 * brute reste tracée pour l'audit et le diagnostic.
 */
public record PaymentOrder(
    String id,
    String rawStatus,
    PaymentStatus status,
    String provider,
    String method,
    String providerReference,
    String redirectUrl,
    BigDecimal amount,
    String currency,
    Instant createdAt,
    Instant updatedAt
) {}
