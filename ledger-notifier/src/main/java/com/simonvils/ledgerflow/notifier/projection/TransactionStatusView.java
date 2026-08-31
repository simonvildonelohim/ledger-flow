package com.simonvils.ledgerflow.notifier.projection;

import java.time.Instant;
import java.util.UUID;

/**
 * What this service knows about a transaction's status.
 *
 * @param transactionId the transaction this describes
 * @param accountId     account it belongs to
 * @param amountMinor   amount in minor units
 * @param currency      ISO-4217 alphabetic code
 * @param status        current status as this service sees it
 * @param acceptedAt    when ledger-api accepted the transaction
 * @param updatedAt     when this row was last written
 */
public record TransactionStatusView(
        UUID transactionId,
        String accountId,
        long amountMinor,
        String currency,
        String status,
        Instant acceptedAt,
        Instant updatedAt) {}
