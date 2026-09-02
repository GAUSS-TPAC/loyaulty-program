package com.yowyob.loyalty.infrastructure.persistence.bonification.repository;

import com.yowyob.loyalty.infrastructure.persistence.bonification.entity.BonificationTransactionEntity;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;

import java.util.UUID;

public interface BonificationTransactionR2dbcRepository
        extends ReactiveCrudRepository<BonificationTransactionEntity, UUID> {
}
