package com.yowyob.loyalty.api.wallet.dto.request;

import com.yowyob.loyalty.domain.wallet.model.PaymentMethod;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;

/**
 * @param provider       MYCOOLPAY ou STRIPE ; à défaut, le provider configuré par défaut
 * @param payerReference numéro Mobile Money au format E.164 (obligatoire si method=MOBILE_MONEY)
 */
public record TopUpRequest(
    @NotNull @Positive BigDecimal amount,
    String provider,
    @NotNull PaymentMethod method,
    String payerReference,
    String idempotencyKey
) {}
