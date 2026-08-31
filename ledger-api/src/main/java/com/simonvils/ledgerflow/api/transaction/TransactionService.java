package com.simonvils.ledgerflow.api.transaction;

import com.simonvils.ledgerflow.api.correlation.CorrelationId;
import com.simonvils.ledgerflow.api.outbox.OutboxEvent;
import com.simonvils.ledgerflow.api.outbox.OutboxEventRepository;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

/** Application logic for accepting transactions into the ledger. */
@Service
public class TransactionService {

    /** Event type published when a transaction enters the ledger. */
    public static final String TRANSACTION_ACCEPTED = "TransactionAccepted";

    private final TransactionRepository transactions;
    private final OutboxEventRepository outbox;
    private final JsonMapper jsonMapper;

    public TransactionService(
            TransactionRepository transactions,
            OutboxEventRepository outbox,
            JsonMapper jsonMapper) {
        this.transactions = transactions;
        this.outbox = outbox;
        this.jsonMapper = jsonMapper;
    }

    /**
     * Accepts a transaction, or returns the one already recorded under this key.
     *
     * <p>The ledger row and its outbox event are written in one database
     * transaction. That is the whole point of ADR-0002: if the two writes could
     * commit separately, a crash between them would leave a transaction recorded
     * that no consumer ever hears about, and nothing downstream would notice.
     * Because both rows commit together, an accepted transaction always has an
     * event waiting for it.
     *
     * <p>A replay writes nothing. The event for that transaction was already
     * written when the key was first seen, and emitting a second one would tell
     * consumers the transaction happened twice — reintroducing, one layer down,
     * exactly the duplicate the idempotency key exists to prevent.
     *
     * <p>The key comes from the client, which is what makes a retry safe: a caller
     * that times out and resends gets its original transaction back instead of a
     * second charge. Only the caller knows whether two requests represent the same
     * intent, so only the caller can supply that identity.
     *
     * <p>Note that a replay returns the stored transaction, not the one just built
     * from the incoming payload. If a client reuses a key with a different amount,
     * the first submission is what stands — the key identifies the operation, and
     * honouring the second body would make the endpoint non-idempotent by another
     * route.
     *
     * <p>The correlation id is copied out of the MDC and into the outbox row.
     * Reading it here rather than passing it down from the controller keeps the
     * signature about the transaction; writing it to the row rather than relying
     * on the thread is what lets the relay, running minutes later on a different
     * thread, still log under the request that caused the event.
     */
    @Transactional
    public TransactionAcceptance accept(String idempotencyKey, CreateTransactionRequest request) {
        Transaction candidate =
                Transaction.accept(
                        idempotencyKey, request.accountId(), request.amountMinor(), request.currency());

        if (transactions.insertIfAbsent(candidate)) {
            outbox.insert(
                    OutboxEvent.pending(
                            candidate.id(),
                            TRANSACTION_ACCEPTED,
                            serialize(candidate),
                            MDC.get(CorrelationId.MDC_KEY)));
            return TransactionAcceptance.created(candidate);
        }

        return transactions
                .findByIdempotencyKey(idempotencyKey)
                .map(TransactionAcceptance::replayed)
                .orElseThrow(
                        () ->
                                new IllegalStateException(
                                        "Insert was suppressed by the unique constraint on "
                                                + "idempotency_key, but no row exists under that key. "
                                                + "This should be unreachable under READ COMMITTED, where "
                                                + "the conflicting transaction has committed by the time "
                                                + "the insert returns."));
    }

    private String serialize(Transaction transaction) {
        // Jackson 3 exceptions are unchecked, so there is nothing to catch here.
        // A failure would mean the payload record cannot be serialized at all,
        // which is a defect rather than bad input, and no recovery would make the
        // resulting event correct.
        return jsonMapper.writeValueAsString(TransactionAcceptedPayload.from(transaction));
    }
}
