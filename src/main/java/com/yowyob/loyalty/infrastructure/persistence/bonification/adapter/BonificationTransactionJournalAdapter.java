package com.yowyob.loyalty.infrastructure.persistence.bonification.adapter;

import com.yowyob.loyalty.domain.bonification.model.BonificationTransactionRecord;
import com.yowyob.loyalty.domain.bonification.port.out.BonificationTransactionJournal;
import com.yowyob.loyalty.infrastructure.persistence.bonification.entity.BonificationTransactionEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
public class BonificationTransactionJournalAdapter implements BonificationTransactionJournal {

    private static final Logger log = LoggerFactory.getLogger(BonificationTransactionJournalAdapter.class);

    private final R2dbcEntityTemplate template;

    public BonificationTransactionJournalAdapter(R2dbcEntityTemplate template) {
        this.template = template;
    }

    @Override
    public Mono<Void> record(BonificationTransactionRecord record) {
        // insert() plutôt que save() : l'id est généré côté domaine, donc l'entité arrive
        // avec une clé non nulle et save() partirait sur un UPDATE d'une ligne inexistante.
        return template.insert(toEntity(record))
                .doOnError(e -> log.warn("Journalisation bonification impossible (tx={}): {}",
                        record.transactionId(), e.toString()))
                // Le journal est observationnel : son échec ne doit pas propager d'erreur
                // à la transaction métier déjà acceptée par le partenaire.
                .onErrorResume(e -> Mono.empty())
                .then();
    }

    private BonificationTransactionEntity toEntity(BonificationTransactionRecord record) {
        BonificationTransactionEntity entity = new BonificationTransactionEntity();
        entity.setId(record.id());
        entity.setTenantId(record.tenantId().value());
        entity.setTransactionId(record.transactionId());
        entity.setClientLogin(record.clientLogin());
        entity.setAmount(record.amount());
        entity.setDebit(record.debit());
        entity.setStatus(record.status());
        entity.setSucceeded(record.succeeded());
        entity.setMessage(record.message());
        entity.setSubmittedAt(record.submittedAt());
        return entity;
    }
}
