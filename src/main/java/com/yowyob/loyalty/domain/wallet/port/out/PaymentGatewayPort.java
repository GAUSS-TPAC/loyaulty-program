package com.yowyob.loyalty.domain.wallet.port.out;

import com.yowyob.loyalty.domain.wallet.model.PaymentOrder;
import com.yowyob.loyalty.domain.wallet.model.PaymentOrderRequest;
import com.yowyob.loyalty.domain.shared.model.TenantId;
import reactor.core.publisher.Mono;

/**
 * Passerelle de paiement externe. Implémentée par l'adaptateur Kernel Core
 * (/api/payments/orders) et par un stub en local.
 *
 * <p>Le port ne couvre que l'encaissement : Kernel Core n'expose aucun endpoint de
 * décaissement (payout) dans son OpenAPI, donc les retraits restent hors passerelle.
 */
public interface PaymentGatewayPort {

    /** Ouvre un ordre de paiement. La réponse porte l'URL de redirection à présenter au payeur. */
    Mono<PaymentOrder> initiate(PaymentOrderRequest request);

    /** Force la passerelle à re-interroger le provider, puis renvoie l'état réconcilié. */
    Mono<PaymentOrder> refresh(TenantId tenantId, String orderId);

    /** Lit l'état connu de la passerelle, sans appel au provider. */
    Mono<PaymentOrder> fetch(TenantId tenantId, String orderId);
}
