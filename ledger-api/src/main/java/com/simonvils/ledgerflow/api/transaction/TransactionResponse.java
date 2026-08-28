package com.simonvils.ledgerflow.api.transaction;

import java.time.Instant;
import java.util.UUID;

/**
 * Outgoing representation of a transaction.
 *
 * <p>Separate from {@link Transaction} so that internal fields can be added to
 * the domain record without silently widening the public API. The idempotency
 * key is deliberately absent: it is the caller's own value and echoing it back
 * adds nothing.
 */
public record TransactionResponse(
        UUID id,
        String accountId,
        long amountMinor,
        String currency,
        TransactionStatus status,
        Instant createdAt) {

    public static TransactionResponse from(Transaction transaction) {
        return new TransactionResponse(
                transaction.id(),
                transaction.accountId(),
                transaction.amountMinor(),
                transaction.currency(),
                transaction.status(),
                transaction.createdAt());
    }
}
