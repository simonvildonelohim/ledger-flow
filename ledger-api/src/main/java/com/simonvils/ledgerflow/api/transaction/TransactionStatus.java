package com.simonvils.ledgerflow.api.transaction;

/**
 * Lifecycle of a transaction as seen by the ledger.
 *
 * <p>A transaction is {@code PENDING} the moment it is accepted and written to
 * the ledger. It only leaves that state once a downstream consumer reports back,
 * which is why the terminal states are set by ledger-notifier and never by the
 * intake path.
 */
public enum TransactionStatus {

    /** Accepted and recorded, not yet confirmed downstream. */
    PENDING,

    /** Confirmed downstream. Terminal. */
    SETTLED,

    /** Rejected downstream. Terminal. */
    FAILED
}
