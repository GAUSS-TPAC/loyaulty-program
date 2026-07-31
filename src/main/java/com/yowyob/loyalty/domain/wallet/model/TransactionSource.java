package com.yowyob.loyalty.domain.wallet.model;

public enum TransactionSource {
    TOPUP_MTN,
    TOPUP_ORANGE,
    TOPUP_STRIPE,
    // Recharge encaissée par la passerelle Kernel Core : le provider réel (MYCOOLPAY,
    // STRIPE) et son agrégateur restent tracés sur la PaymentRequest, pas ici.
    TOPUP_GATEWAY,
    LOYALTY_REWARD,
    REFERRAL_BONUS,
    CASHBACK,
    CAMPAIGN_BONUS,
    PURCHASE,
    WITHDRAWAL,
    MANUAL_ADJUSTMENT,
    REVERSAL
}
