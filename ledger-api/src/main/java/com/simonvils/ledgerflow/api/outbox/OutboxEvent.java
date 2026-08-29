package com.simonvils.ledgerflow.api.outbox;

import java.time.Instant;
import java.util.UUID;

/**
 * An event waiting to be published, written in the same database transaction as
 * the ledger row it describes. See ADR-0002 for why the outbox exists.
 *
 * <p>The payload is JSON text rather than a parsed object. The relay's job is to
 * move bytes to the broker, and a relay that deserializes every event only to
 * serialize it again would fail on any event whose class has since changed —
 * turning an old row into a poison message. Keeping it opaque means the relay
 * can publish an event it does not understand.
 *
 * @param id          event identifier, also the deduplication key for consumers
 * @param aggregateId the transaction this event describes
 * @param eventType   what happened, used by consumers to route
 * @param payload     JSON document sent to the broker as-is
 * @param createdAt   when the event was written to the outbox
 * @param publishedAt when the broker acknowledged it, or {@code null} while pending
 */
public record OutboxEvent(
        UUID id,
        UUID aggregateId,
        String eventType,
        String payload,
        Instant createdAt,
        Instant publishedAt) {

    /** Builds an event that has not been published yet. */
    public static OutboxEvent pending(UUID aggregateId, String eventType, String payload) {
        return new OutboxEvent(
                UUID.randomUUID(), aggregateId, eventType, payload, Instant.now(), null);
    }

    /** Whether this event still needs to reach the broker. */
    public boolean isPending() {
        return publishedAt == null;
    }
}
