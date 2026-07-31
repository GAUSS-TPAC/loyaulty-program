package com.yowyob.loyalty.infrastructure.persistence.wallet.adapter;

import com.yowyob.loyalty.domain.wallet.model.PaymentRequest;
import com.yowyob.loyalty.domain.wallet.port.out.PaymentRequestRepository;
import com.yowyob.loyalty.infrastructure.persistence.wallet.mapper.PaymentRequestMapper;
import com.yowyob.loyalty.infrastructure.persistence.wallet.repository.PaymentRequestR2dbcRepository;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.UUID;

@Component
public class PaymentRequestRepositoryAdapter implements PaymentRequestRepository {

    private final PaymentRequestR2dbcRepository repository;
    private final PaymentRequestMapper mapper;
    private final R2dbcEntityTemplate template;

    public PaymentRequestRepositoryAdapter(PaymentRequestR2dbcRepository repository,
                                           PaymentRequestMapper mapper,
                                           R2dbcEntityTemplate template) {
        this.repository = repository;
        this.mapper = mapper;
        this.template = template;
    }

    // L'id est généré côté application, donc ReactiveCrudRepository.save() prendrait la ligne
    // pour une mise à jour et n'insérerait rien (même piège que WalletTransactionRepositoryAdapter) :
    // insert et update sont donc explicitement séparés.
    @Override
    public Mono<PaymentRequest> save(PaymentRequest request) {
        return template.insert(mapper.toEntity(request)).map(mapper::toDomain);
    }

    @Override
    public Mono<PaymentRequest> update(PaymentRequest request) {
        return template.update(mapper.toEntity(request)).map(mapper::toDomain);
    }

    @Override
    public Mono<PaymentRequest> findByExternalRef(String externalRef) {
        return repository.findByExternalRef(externalRef).map(mapper::toDomain);
    }

    @Override
    public Mono<PaymentRequest> findById(UUID id) {
        return repository.findById(id).map(mapper::toDomain);
    }
}
