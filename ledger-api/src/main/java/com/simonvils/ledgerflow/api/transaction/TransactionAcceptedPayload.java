package com.simonvils.ledgerflow.api.transaction;

import java.time.Instant;
import java.util.UUID;

/**
 * The body of a {@code TransactionAccepted} event.
 *
 * <p>This is a published contract, not an internal type. Once a consumer reads
 * these fields, renaming one breaks it, and unlike an HTTP response there is no
 * way to see who depends on it. Kept separate from {@link Transaction} so the
 * domain record can gain fields without every change reaching the broker.
 *
 * <p>The idempotency key is deliberately absent. It belongs to the caller that
 * submitted the transaction and means nothing downstream; consumers deduplicate
 * on the event id instead.
 */
public record TransactionAcceptedPayload(
        UUID transactionId,
        String accountId,
        long amountMinor,
        String currency,
        TransactionStatus status,
        Instant acceptedAt) {

    public static TransactionAcceptedPayload from(Transaction transaction) {
        return new TransactionAcceptedPayload(
                transaction.id(),
                transaction.accountId(),
                transaction.amountMinor(),
                transaction.currency(),
                transaction.status(),
                transaction.createdAt());
    }
}
