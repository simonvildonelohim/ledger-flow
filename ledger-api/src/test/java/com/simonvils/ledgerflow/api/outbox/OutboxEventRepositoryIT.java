package com.simonvils.ledgerflow.api.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import com.simonvils.ledgerflow.api.AbstractPostgresIT;
import com.simonvils.ledgerflow.api.transaction.Transaction;
import com.simonvils.ledgerflow.api.transaction.TransactionRepository;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.simple.JdbcClient;

/** Round-trip tests for {@link OutboxEventRepository} against a real PostgreSQL. */
class OutboxEventRepositoryIT extends AbstractPostgresIT {

    @Autowired private OutboxEventRepository outbox;

    @Autowired private TransactionRepository transactions;

    /**
     * Used for verification reads only. {@code findPending} deliberately hides
     * published rows, so asserting on one needs a query the production API does
     * not offer — and adding a production method to serve a test would be the
     * wrong trade.
     */
    @Autowired private JdbcClient jdbcClient;

    @Test
    void insertsAndReadsBackAPendingEvent() {
        UUID aggregateId = someTransaction();
        OutboxEvent event =
                OutboxEvent.pending(aggregateId, "TransactionAccepted", "{\"amountMinor\":12500}");

        outbox.insert(event);

        OutboxEvent stored = readBack(event.id());
        assertThat(stored.aggregateId()).isEqualTo(aggregateId);
        assertThat(stored.eventType()).isEqualTo("TransactionAccepted");
        assertThat(stored.publishedAt()).isNull();
        assertThat(stored.isPending()).isTrue();
        assertThat(stored.createdAt()).isNotNull();
    }

    @Test
    void storesTheCorrelationIdOfTheRequestThatProducedTheEvent() {
        UUID aggregateId = someTransaction();
        String correlationId = "req-" + UUID.randomUUID();
        OutboxEvent event =
                OutboxEvent.pending(aggregateId, "TransactionAccepted", "{}", correlationId);

        outbox.insert(event);

        // The relay publishes minutes later on a scheduler thread. Without the
        // column, the chain from request to publication would break here.
        assertThat(readBack(event.id()).correlationId()).isEqualTo(correlationId);
    }

    @Test
    void acceptsAnEventWithNoCorrelationId() {
        UUID aggregateId = someTransaction();
        OutboxEvent event = OutboxEvent.pending(aggregateId, "TransactionAccepted", "{}");

        outbox.insert(event);

        assertThat(readBack(event.id()).correlationId()).isNull();
    }

    @Test
    void preservesThePayloadAsJson() {
        UUID aggregateId = someTransaction();
        String payload =
                "{\"transactionId\":\"abc\",\"amountMinor\":-4250,\"currency\":\"EUR\","
                        + "\"nested\":{\"ok\":true}}";
        OutboxEvent event = OutboxEvent.pending(aggregateId, "TransactionAccepted", payload);

        outbox.insert(event);

        // jsonb normalises whitespace and key order, so assert on content rather
        // than on an exact string match.
        String stored = readBack(event.id()).payload();
        assertThat(stored).contains("-4250").contains("EUR").contains("true");
    }

    @Test
    void rejectsAMalformedPayloadAtWriteTime() {
        UUID aggregateId = someTransaction();
        OutboxEvent event = OutboxEvent.pending(aggregateId, "TransactionAccepted", "{ not json");

        // The jsonb column is what catches this. A text column would have stored
        // it happily and broken a consumer instead.
        assertThatThrownBy(() -> outbox.insert(event)).isInstanceOf(RuntimeException.class);
    }

    @Test
    void rejectsAnEventForATransactionThatDoesNotExist() {
        OutboxEvent orphan = OutboxEvent.pending(UUID.randomUUID(), "TransactionAccepted", "{}");

        assertThatThrownBy(() -> outbox.insert(orphan))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void returnsPendingEventsOldestFirst() {
        UUID aggregateId = someTransaction();
        Instant now = Instant.now();
        OutboxEvent older = eventAt(aggregateId, now.minus(10, ChronoUnit.MINUTES));
        OutboxEvent newer = eventAt(aggregateId, now.minus(5, ChronoUnit.MINUTES));
        // Inserted newest first, so a passing assertion cannot come from insertion
        // order alone.
        outbox.insert(newer);
        outbox.insert(older);

        List<UUID> pendingIds =
                outbox.findPending(1000).stream()
                        .map(OutboxEvent::id)
                        .filter(id -> id.equals(older.id()) || id.equals(newer.id()))
                        .toList();

        assertThat(pendingIds).containsExactly(older.id(), newer.id());
    }

    @Test
    void respectsTheLimit() {
        UUID aggregateId = someTransaction();
        outbox.insert(OutboxEvent.pending(aggregateId, "TransactionAccepted", "{}"));
        outbox.insert(OutboxEvent.pending(aggregateId, "TransactionAccepted", "{}"));

        assertThat(outbox.findPending(1)).hasSize(1);
    }

    @Test
    void aPublishedEventIsNoLongerPending() {
        UUID aggregateId = someTransaction();
        OutboxEvent event = OutboxEvent.pending(aggregateId, "TransactionAccepted", "{}");
        outbox.insert(event);

        assertThat(outbox.markPublished(event.id(), Instant.now())).isTrue();

        assertThat(readBack(event.id()).publishedAt()).isNotNull();
        assertThat(outbox.findPending(1000)).extracting(OutboxEvent::id).doesNotContain(event.id());
    }

    @Test
    void markingAnAlreadyPublishedEventChangesNothing() {
        UUID aggregateId = someTransaction();
        OutboxEvent event = OutboxEvent.pending(aggregateId, "TransactionAccepted", "{}");
        outbox.insert(event);
        Instant firstPublish = Instant.now().minus(1, ChronoUnit.HOURS);
        outbox.markPublished(event.id(), firstPublish);

        // A second relay instance attempting the same claim must lose it.
        boolean claimed = outbox.markPublished(event.id(), Instant.now());

        assertThat(claimed).isFalse();
        assertThat(readBack(event.id()).publishedAt())
                .isCloseTo(firstPublish, within(1, ChronoUnit.SECONDS));
    }

    private OutboxEvent eventAt(UUID aggregateId, Instant createdAt) {
        return new OutboxEvent(
                UUID.randomUUID(), aggregateId, "TransactionAccepted", "{}", createdAt, null, null);
    }

    /** The outbox has a foreign key to transactions, so a parent row must exist. */
    private UUID someTransaction() {
        Transaction transaction =
                Transaction.accept("key-" + UUID.randomUUID(), "acct-outbox", 100L, "CAD");
        transactions.insertIfAbsent(transaction);
        return transaction.id();
    }

    private OutboxEvent readBack(UUID id) {
        return jdbcClient
                .sql(
                        """
                        SELECT id, aggregate_id, event_type, payload, created_at, published_at,
                               correlation_id
                        FROM outbox_event
                        WHERE id = :id
                        """)
                .param("id", id)
                .query(
                        (rs, rowNum) -> {
                            OffsetDateTime publishedAt =
                                    rs.getObject("published_at", OffsetDateTime.class);
                            return new OutboxEvent(
                                    rs.getObject("id", UUID.class),
                                    rs.getObject("aggregate_id", UUID.class),
                                    rs.getString("event_type"),
                                    rs.getString("payload"),
                                    rs.getObject("created_at", OffsetDateTime.class).toInstant(),
                                    publishedAt == null ? null : publishedAt.toInstant(),
                                    rs.getString("correlation_id"));
                        })
                .single();
    }
}
