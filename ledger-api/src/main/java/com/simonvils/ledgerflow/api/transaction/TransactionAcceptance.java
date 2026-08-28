package com.simonvils.ledgerflow.api.transaction;

/**
 * Outcome of an intake attempt.
 *
 * <p>The distinction is carried explicitly rather than inferred, because the
 * caller cannot tell the two apart from the transaction alone: a replay returns
 * the original record, which looks identical to what a first request would have
 * produced. The HTTP layer needs the difference to answer 201 or 200.
 *
 * @param transaction the stored transaction, whether just written or pre-existing
 * @param created     {@code true} if this call wrote it, {@code false} on a replay
 */
public record TransactionAcceptance(Transaction transaction, boolean created) {

    static TransactionAcceptance created(Transaction transaction) {
        return new TransactionAcceptance(transaction, true);
    }

    static TransactionAcceptance replayed(Transaction transaction) {
        return new TransactionAcceptance(transaction, false);
    }
}
