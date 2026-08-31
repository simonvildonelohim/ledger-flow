package com.simonvils.ledgerflow.notifier.projection;

import com.simonvils.ledgerflow.notifier.messaging.TransactionAcceptedEvent;
import com.simonvils.ledgerflow.notifier.messaging.TransactionEventHandler;
import java.time.Instant;
import org.springframework.stereotype.Component;

/**
 * Turns consumed events into a status this service can be asked about.
 *
 * <p>Being a {@link TransactionEventHandler} is what makes this safe: the
 * processor runs handlers inside the transaction that claims the event id, so
 * the claim and the status change commit together. Nothing here has to know that
 * — which is the point of putting the transaction boundary one level up.
 */
@Component
public class TransactionStatusProjection implements TransactionEventHandler {

    /**
     * Status recorded once an accepted transaction has been consumed.
     *
     * <p>A simplification, recorded in ADR-0004. Real settlement is a separate
     * event emitted after a downstream system confirms the money moved; nothing
     * here produces that confirmation yet, so consumption stands in for it.
     */
    static final String SETTLED = "SETTLED";

    private final TransactionStatusRepository repository;

    public TransactionStatusProjection(TransactionStatusRepository repository) {
        this.repository = repository;
    }

    @Override
    public void handle(TransactionAcceptedEvent event) {
        repository.save(
                new TransactionStatusView(
                        event.transactionId(),
                        event.accountId(),
                        event.amountMinor(),
                        event.currency(),
                        SETTLED,
                        event.acceptedAt(),
                        Instant.now()));
    }
}
