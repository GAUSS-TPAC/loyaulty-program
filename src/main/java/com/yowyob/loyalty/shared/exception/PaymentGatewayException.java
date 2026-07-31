package com.yowyob.loyalty.shared.exception;

/**
 * La passerelle de paiement a répondu, mais a refusé ou invalidé l'opération (4xx, ou
 * enveloppe {@code success=false}). Distinct de {@link KernelCoreUnavailableException},
 * qui signale une panne : ici, rejouer à l'identique redonnera la même réponse.
 */
public class PaymentGatewayException extends AppException {
    public PaymentGatewayException(String detail) {
        super(ErrorCode.PAYMENT_GATEWAY_ERROR, detail);
    }
}
