package com.yowyob.loyalty.domain.wallet.model;

import com.yowyob.loyalty.domain.shared.model.TenantId;

import java.math.BigDecimal;

/**
 * Demande d'ouverture d'un ordre de paiement auprès de la passerelle.
 *
 * @param payerReference identifiant du payeur côté provider — numéro Mobile Money en E.164
 *                       pour {@link PaymentMethod#MOBILE_MONEY}, référence carte/client pour
 *                       {@link PaymentMethod#CARD}.
 */
public record PaymentOrderRequest(
    TenantId tenantId,
    BigDecimal amount,
    String currency,
    String provider,
    PaymentMethod method,
    String payerReference,
    String description,
    String idempotencyKey
) {}
