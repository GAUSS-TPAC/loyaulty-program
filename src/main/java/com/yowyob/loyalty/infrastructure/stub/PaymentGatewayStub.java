package com.yowyob.loyalty.infrastructure.stub;

import com.yowyob.loyalty.domain.shared.model.TenantId;
import com.yowyob.loyalty.domain.wallet.model.PaymentOrder;
import com.yowyob.loyalty.domain.wallet.model.PaymentOrderRequest;
import com.yowyob.loyalty.domain.wallet.model.PaymentStatus;
import com.yowyob.loyalty.domain.wallet.port.out.PaymentGatewayPort;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Passerelle de paiement en mémoire, active tant que
 * {@code app.kernel-core.payments.enabled} est faux.
 *
 * <p>Un ordre est ouvert en PENDING puis passe à COMPLETED au premier
 * {@code refresh} — de quoi dérouler le parcours de recharge en local sans Kernel Core.
 * Aucun encaissement réel n'a lieu.
 */
@Component
@ConditionalOnProperty(prefix = "app.kernel-core.payments", name = "enabled",
        havingValue = "false", matchIfMissing = true)
public class PaymentGatewayStub implements PaymentGatewayPort {

    private static final Logger log = LoggerFactory.getLogger(PaymentGatewayStub.class);

    private final Map<String, PaymentOrder> orders = new ConcurrentHashMap<>();

    @PostConstruct
    void warn() {
        log.warn("PaymentGatewayStub actif : les recharges de wallet ne sont PAS encaissées. "
                + "Activer app.kernel-core.payments.enabled pour utiliser Kernel Core.");
    }

    @Override
    public Mono<PaymentOrder> initiate(PaymentOrderRequest request) {
        String id = UUID.randomUUID().toString();
        Instant now = Instant.now();
        PaymentOrder order = new PaymentOrder(
                id,
                "PENDING",
                PaymentStatus.PENDING,
                request.provider(),
                request.method().name(),
                "STUB-" + id.substring(0, 8),
                "https://stub.local/pay/" + id,
                request.amount(),
                request.currency(),
                now,
                now);
        orders.put(id, order);
        return Mono.just(order);
    }

    @Override
    public Mono<PaymentOrder> refresh(TenantId tenantId, String orderId) {
        PaymentOrder existing = orders.get(orderId);
        if (existing == null) {
            return Mono.empty();
        }
        PaymentOrder completed = new PaymentOrder(
                existing.id(), "SUCCESS", PaymentStatus.COMPLETED, existing.provider(), existing.method(),
                existing.providerReference(), existing.redirectUrl(), existing.amount(), existing.currency(),
                existing.createdAt(), Instant.now());
        orders.put(orderId, completed);
        return Mono.just(completed);
    }

    @Override
    public Mono<PaymentOrder> fetch(TenantId tenantId, String orderId) {
        return Mono.justOrEmpty(orders.get(orderId));
    }

    /** Exposé pour les tests : montant total ouvert par le stub. */
    public BigDecimal totalInitiated() {
        return orders.values().stream()
                .map(PaymentOrder::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
