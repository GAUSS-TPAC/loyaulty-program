package com.yowyob.loyalty.domain.bonification.port.out;

import com.yowyob.loyalty.domain.bonification.model.BonificationTransactionRecord;
import reactor.core.publisher.Mono;

/**
 * Journal append-only des soumissions vers l'API partenaire Bonification.
 *
 * <p>Purement observationnel : une écriture qui échoue ne doit jamais faire échouer
 * la transaction métier qu'elle décrit, c'est à l'appelant de neutraliser l'erreur.
 */
public interface BonificationTransactionJournal {

    Mono<Void> record(BonificationTransactionRecord record);
}
