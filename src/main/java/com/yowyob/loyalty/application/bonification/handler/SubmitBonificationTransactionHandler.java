package com.yowyob.loyalty.application.bonification.handler;

import com.yowyob.loyalty.application.bonification.BonificationCredentialsResolver;
import com.yowyob.loyalty.domain.bonification.model.BonificationTransactionRecord;
import com.yowyob.loyalty.domain.bonification.model.BonificationTransactionRequest;
import com.yowyob.loyalty.domain.bonification.model.BonificationTransactionResult;
import com.yowyob.loyalty.domain.bonification.port.out.BonificationPort;
import com.yowyob.loyalty.domain.bonification.port.out.BonificationTransactionJournal;
import com.yowyob.loyalty.domain.loyalty.model.event.IncomingEvent;
import com.yowyob.loyalty.domain.shared.model.TenantId;
import com.yowyob.loyalty.shared.multitenancy.TenantContextHolder;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Instant;

@Service
public class SubmitBonificationTransactionHandler {

    private final BonificationPort bonificationPort;
    private final BonificationCredentialsResolver credentialsResolver;
    private final BonificationTransactionJournal journal;

    public SubmitBonificationTransactionHandler(
            BonificationPort bonificationPort,
            BonificationCredentialsResolver credentialsResolver,
            BonificationTransactionJournal journal
    ) {
        this.bonificationPort = bonificationPort;
        this.credentialsResolver = credentialsResolver;
        this.journal = journal;
    }

    public Mono<BonificationTransactionResult> submit(BonificationTransactionRequest request) {
        return TenantContextHolder.getTenantId()
                .flatMap((TenantId tenantId) -> submitForTenant(tenantId, request));
    }

    public Mono<BonificationTransactionResult> submitFromLoyaltyEvent(IncomingEvent event) {
        return mapEventToRequest(event)
                .flatMap(this::submit);
    }

    private Mono<BonificationTransactionRequest> mapEventToRequest(IncomingEvent event) {
        String clientLogin = event.getPayloadString("clientLogin")
                .or(() -> event.getPayloadString("client_login"))
                .orElseGet(() -> event.memberId().value().toString());

        return event.getPayloadDecimal("amount")
                .map(amount -> {
                    boolean debit = event.getPayloadValue("isDebit")
                            .map(v -> Boolean.parseBoolean(v.toString()))
                            .orElse(false);
                    BonificationTransactionRequest request = debit
                            ? BonificationTransactionRequest.debit(amount.doubleValue(), clientLogin)
                            : BonificationTransactionRequest.credit(amount.doubleValue(), clientLogin);
                    return request;
                })
                .map(Mono::just)
                .orElseGet(() -> Mono.error(new IllegalArgumentException(
                        "Le champ payload.amount est requis pour transmettre l'événement à l'API Bonification")));
    }

    /**
     * Point de passage unique vers le partenaire : c'est ici — et nulle part ailleurs —
     * que la soumission est journalisée, succès comme échec. Le journal alimente les
     * statistiques ({@code /analytics/bonuses}) ; sans la branche d'erreur, un partenaire
     * en panne afficherait un taux de succès de 100 % sur zéro transaction.
     */
    public Mono<BonificationTransactionResult> submitForTenant(
            TenantId tenantId,
            BonificationTransactionRequest request
    ) {
        return credentialsResolver.resolve(tenantId)
                .flatMap(credentials -> bonificationPort.submitTransaction(tenantId, credentials, request))
                .flatMap(result -> journal
                        .record(BonificationTransactionRecord.of(tenantId, result, Instant.now()))
                        .thenReturn(result))
                .onErrorResume(error -> journal
                        .record(BonificationTransactionRecord.failed(
                                tenantId, request, error.toString(), Instant.now()))
                        .then(Mono.error(error)));
    }
}
