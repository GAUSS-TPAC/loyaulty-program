package com.yowyob.loyalty.infrastructure.persistence.wallet.repository;

import com.yowyob.loyalty.infrastructure.persistence.wallet.entity.PaymentRequestEntity;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.Collection;
import java.util.UUID;

public interface PaymentRequestR2dbcRepository extends ReactiveCrudRepository<PaymentRequestEntity, UUID> {

    Mono<PaymentRequestEntity> findByExternalRef(String externalRef);

    Flux<PaymentRequestEntity> findByWalletIdOrderByInitiatedAtDesc(UUID walletId);

    /** Recharges encore ouvertes dont la fenêtre est dépassée — matière à réconciliation. */
    Flux<PaymentRequestEntity> findByStatusInAndExpiresAtBefore(Collection<String> statuses, Instant cutoff);
}
