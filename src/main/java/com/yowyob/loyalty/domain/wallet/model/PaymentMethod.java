package com.yowyob.loyalty.domain.wallet.model;

/**
 * Moyen de paiement supporté par la passerelle Kernel Core
 * (POST /api/payments/orders, champ "method").
 */
public enum PaymentMethod {
    MOBILE_MONEY,
    CARD
}
