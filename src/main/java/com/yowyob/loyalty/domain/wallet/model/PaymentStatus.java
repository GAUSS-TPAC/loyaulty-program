package com.yowyob.loyalty.domain.wallet.model;

public enum PaymentStatus {
    INITIATED,
    PENDING,
    COMPLETED,
    FAILED,
    CANCELLED,
    EXPIRED;

    /** Un statut final ne sera plus modifié par un rafraîchissement ni par un callback. */
    public boolean isFinal() {
        return this == COMPLETED || this == FAILED || this == CANCELLED || this == EXPIRED;
    }

    public boolean isSuccessful() {
        return this == COMPLETED;
    }
}
