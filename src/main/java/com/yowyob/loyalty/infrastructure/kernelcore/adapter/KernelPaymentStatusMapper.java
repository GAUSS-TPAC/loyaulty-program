package com.yowyob.loyalty.infrastructure.kernelcore.adapter;

import com.yowyob.loyalty.domain.wallet.model.PaymentStatus;

import java.util.Set;

/**
 * Traduit le statut d'une PaymentOrder Kernel Core vers {@link PaymentStatus}.
 *
 * <p>L'OpenAPI de Kernel Core type {@code PaymentOrderResponse.status} en {@code string}
 * sans énumération : impossible de générer un mapping exhaustif depuis le contrat. On
 * accepte donc les libellés observés chez les deux providers (MyCoolPay, Stripe) et sur les
 * autres ressources du même backend qui, elles, publient leur énumération
 * ({@code PENDING_PAYMENT}/{@code RECHARGED}/{@code FAILED}/{@code CANCELLED} sur
 * WalletRechargeResponse).
 *
 * <p>Règle de sûreté : un libellé inconnu ne vaut jamais succès — il reste
 * {@link PaymentStatus#PENDING}, donc le wallet n'est pas crédité et le prochain
 * rafraîchissement retentera.
 */
public final class KernelPaymentStatusMapper {

    private static final Set<String> SUCCESS = Set.of(
            "SUCCESS", "SUCCEEDED", "COMPLETED", "COMPLETE", "PAID", "CONFIRMED", "RECHARGED", "CAPTURED", "SETTLED");

    private static final Set<String> FAILURE = Set.of(
            "FAILED", "FAILURE", "ERROR", "REJECTED", "DECLINED", "REFUSED");

    private static final Set<String> CANCELLED = Set.of(
            "CANCELLED", "CANCELED", "VOID", "VOIDED", "REFUNDED", "REVERSED");

    private static final Set<String> EXPIRED = Set.of("EXPIRED", "TIMEOUT", "TIMED_OUT");

    private static final Set<String> INITIATED = Set.of("INITIATED", "CREATED", "NEW", "DRAFT");

    private KernelPaymentStatusMapper() {}

    public static PaymentStatus map(String rawStatus) {
        if (rawStatus == null || rawStatus.isBlank()) {
            return PaymentStatus.PENDING;
        }
        String normalized = rawStatus.trim().toUpperCase();
        if (SUCCESS.contains(normalized)) return PaymentStatus.COMPLETED;
        if (FAILURE.contains(normalized)) return PaymentStatus.FAILED;
        if (CANCELLED.contains(normalized)) return PaymentStatus.CANCELLED;
        if (EXPIRED.contains(normalized)) return PaymentStatus.EXPIRED;
        if (INITIATED.contains(normalized)) return PaymentStatus.INITIATED;
        return PaymentStatus.PENDING;
    }
}
