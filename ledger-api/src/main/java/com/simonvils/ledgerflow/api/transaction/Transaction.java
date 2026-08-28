package com.simonvils.ledgerflow.api.transaction;

import java.time.Instant;
import java.util.UUID;

/**
 * A single accepted transaction in the ledger.
 *
 * <p><strong>Amounts are held in minor units</strong> — cents, not dollars — as a
 * {@code long}. Binary floating point cannot represent most decimal fractions
 * exactly, so {@code double} and {@code float} are never acceptable for money:
 * the error is small per operation and unbounded across a ledger. A {@code long}
 * of minor units is exact, and comfortably covers any realistic balance.
 *
 * @param id             ledger identifier, assigned by this service
 * @param idempotencyKey client-supplied key that makes retries safe
 * @param accountId      account the transaction belongs to
 * @param amountMinor    amount in minor units; may be negative for a debit
 * @param currency       ISO-4217 alphabetic code, uppercase
 * @param status         current lifecycle state
 * @param createdAt      instant the transaction was accepted
 */
public record Transaction(
        UUID id,
        String idempotencyKey,
        String accountId,
        long amountMinor,
        String currency,
        TransactionStatus status,
        Instant createdAt) {

    /**
     * Builds a newly accepted transaction: fresh identifier, current timestamp,
     * {@link TransactionStatus#PENDING} status.
     *
     * <p>The identifier is generated here rather than by the database so the
     * caller knows it before the insert. That matters from M3 onward, when the
     * outbox row written in the same transaction has to reference it.
     */
    public static Transaction accept(
            String idempotencyKey, String accountId, long amountMinor, String currency) {
        return new Transaction(
                UUID.randomUUID(),
                idempotencyKey,
                accountId,
                amountMinor,
                currency,
                TransactionStatus.PENDING,
                Instant.now());
    }
}
