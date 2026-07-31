package com.yowyob.loyalty.infrastructure.kernelcore;

import com.yowyob.loyalty.domain.wallet.model.PaymentStatus;
import com.yowyob.loyalty.infrastructure.kernelcore.adapter.KernelPaymentStatusMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class KernelPaymentStatusMapperTest {

    @ParameterizedTest
    @ValueSource(strings = {"SUCCESS", "succeeded", "COMPLETED", "Paid", "RECHARGED", "CAPTURED"})
    void maps_success_labels_to_completed(String raw) {
        assertThat(KernelPaymentStatusMapper.map(raw)).isEqualTo(PaymentStatus.COMPLETED);
    }

    @ParameterizedTest
    @ValueSource(strings = {"FAILED", "error", "REJECTED", "DECLINED"})
    void maps_failure_labels_to_failed(String raw) {
        assertThat(KernelPaymentStatusMapper.map(raw)).isEqualTo(PaymentStatus.FAILED);
    }

    @Test
    void maps_pending_payment_label_published_by_kernel_core() {
        // WalletRechargeResponse est la seule ressource du backend Kernel Core qui publie
        // son énumération de statut : PENDING_PAYMENT y désigne un paiement non encore reçu.
        assertThat(KernelPaymentStatusMapper.map("PENDING_PAYMENT")).isEqualTo(PaymentStatus.PENDING);
    }

    @Test
    void maps_cancelled_and_expired_labels() {
        assertThat(KernelPaymentStatusMapper.map("CANCELLED")).isEqualTo(PaymentStatus.CANCELLED);
        assertThat(KernelPaymentStatusMapper.map("TIMEOUT")).isEqualTo(PaymentStatus.EXPIRED);
    }

    @Test
    void unknown_label_never_reads_as_success() {
        // Le statut est un string libre dans l'OpenAPI : un libellé inattendu ne doit jamais
        // déclencher de crédit, seulement une nouvelle tentative de réconciliation.
        assertThat(KernelPaymentStatusMapper.map("SOMETHING_NEW")).isEqualTo(PaymentStatus.PENDING);
        assertThat(KernelPaymentStatusMapper.map(null)).isEqualTo(PaymentStatus.PENDING);
        assertThat(KernelPaymentStatusMapper.map("  ")).isEqualTo(PaymentStatus.PENDING);
    }
}
