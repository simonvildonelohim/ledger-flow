package com.simonvils.ledgerflow.notifier.messaging;

import java.time.Instant;
import java.util.UUID;

/**
 * A {@code TransactionAccepted} event as this service reads it.
 *
 * <p>Deliberately a separate declaration from the record ledger-api serializes,
 * rather than a shared class in a common module. The two services are separate
 * deployables: sharing the type would mean neither can change its own view of
 * the payload without rebuilding the other, which is the coupling that message
 * brokers exist to remove. Duplication here is the cheaper problem.
 *
 * <p>Unknown fields are ignored on purpose, so the producer can add one without
 * breaking this consumer. Only a removed or renamed field is a breaking change,
 * and that ships as a new topic version.
 */
public record TransactionAcceptedEvent(
        UUID transactionId,
        String accountId,
        long amountMinor,
        String currency,
        String status,
        Instant acceptedAt) {}
