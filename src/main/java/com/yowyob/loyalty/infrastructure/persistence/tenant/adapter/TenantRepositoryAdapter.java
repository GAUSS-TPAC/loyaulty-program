package com.yowyob.loyalty.infrastructure.persistence.tenant.adapter;

import com.yowyob.loyalty.domain.shared.model.TenantId;
import com.yowyob.loyalty.domain.tenant.model.Tenant;
import com.yowyob.loyalty.domain.tenant.port.out.TenantRepository;
import com.yowyob.loyalty.infrastructure.persistence.tenant.entity.TenantEntity;
import com.yowyob.loyalty.infrastructure.persistence.tenant.mapper.TenantMapper;
import com.yowyob.loyalty.infrastructure.persistence.tenant.repository.TenantR2dbcRepository;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
public class TenantRepositoryAdapter implements TenantRepository {

    private final TenantR2dbcRepository r2dbcRepo;
    private final TenantMapper mapper;
    private final R2dbcEntityTemplate template;

    public TenantRepositoryAdapter(TenantR2dbcRepository r2dbcRepo, TenantMapper mapper,
                                   R2dbcEntityTemplate template) {
        this.r2dbcRepo = r2dbcRepo;
        this.mapper = mapper;
        this.template = template;
    }

    @Override
    public Mono<Tenant> findById(TenantId id) {
        return r2dbcRepo.findById(id.value()).map(mapper::toDomain);
    }

    @Override
    public Mono<Tenant> findBySlug(String slug) {
        return r2dbcRepo.findBySlug(slug).map(mapper::toDomain);
    }

    @Override
    public Mono<Boolean> existsById(TenantId id) {
        return r2dbcRepo.existsById(id.value());
    }

    // Id UUID généré côté client et pas de Persistable => save() émettrait toujours un UPDATE,
    // qui ne touche aucune ligne quand le tenant n'existe pas encore localement : l'écriture
    // paraissait réussir et rien n'était persisté. On décide insert vs update explicitement,
    // comme les autres adaptateurs de ce paquet.
    @Override
    public Mono<Tenant> save(Tenant tenant) {
        TenantEntity entity = mapper.toEntity(tenant);
        return r2dbcRepo.existsById(entity.getId())
                .flatMap(exists -> exists ? r2dbcRepo.save(entity) : template.insert(entity))
                .map(mapper::toDomain);
    }
}
