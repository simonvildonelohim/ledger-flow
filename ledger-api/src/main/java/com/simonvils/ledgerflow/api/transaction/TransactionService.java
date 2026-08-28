package com.simonvils.ledgerflow.api.transaction;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Application logic for accepting transactions into the ledger. */
@Service
public class TransactionService {

    private final TransactionRepository repository;

    public TransactionService(TransactionRepository repository) {
        this.repository = repository;
    }

    /**
     * Accepts a transaction, or returns the one already recorded under this key.
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
     * <p>{@code @Transactional} is not needed for a single insert, but from M3 this
     * method also writes the outbox row, and the guarantee in ADR-0002 only holds
     * if both writes commit together.
     */
    @Transactional
    public TransactionAcceptance accept(String idempotencyKey, CreateTransactionRequest request) {
        Transaction candidate =
                Transaction.accept(
                        idempotencyKey, request.accountId(), request.amountMinor(), request.currency());

        if (repository.insertIfAbsent(candidate)) {
            return TransactionAcceptance.created(candidate);
        }

        return repository
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
}
