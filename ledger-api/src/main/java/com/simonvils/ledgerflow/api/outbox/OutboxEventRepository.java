package com.simonvils.ledgerflow.api.outbox;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Persistence for {@link OutboxEvent}, over the {@code outbox_event} table
 * created by {@code V1__create_ledger_and_outbox.sql}.
 */
@Repository
public class OutboxEventRepository {

    private static final String INSERT_SQL =
            """
            INSERT INTO outbox_event
                (id, aggregate_id, event_type, payload, created_at, published_at, correlation_id)
            VALUES
                (:id, :aggregateId, :eventType, CAST(:payload AS jsonb), :createdAt, :publishedAt,
                 :correlationId)
            """;

    private static final String SELECT_COLUMNS =
            "id, aggregate_id, event_type, payload, created_at, published_at, correlation_id";

    private static final String SELECT_PENDING_SQL =
            "SELECT "
                    + SELECT_COLUMNS
                    + """
                     FROM outbox_event
                     WHERE published_at IS NULL
                     ORDER BY created_at
                     LIMIT :limit
                    """;

    private static final String CLAIM_PENDING_SQL =
            "SELECT "
                    + SELECT_COLUMNS
                    + """
                     FROM outbox_event
                     WHERE published_at IS NULL
                     ORDER BY created_at
                     LIMIT :limit
                     FOR UPDATE SKIP LOCKED
                    """;

    private static final String MARK_PUBLISHED_SQL =
            """
            UPDATE outbox_event
            SET published_at = :publishedAt
            WHERE id = :id AND published_at IS NULL
            """;

    private final JdbcClient jdbcClient;

    public OutboxEventRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    /**
     * Writes an event.
     *
     * <p>The payload is cast explicitly with {@code CAST(? AS jsonb)}. The driver
     * sends a Java string as {@code character varying}, and PostgreSQL will not
     * coerce that into {@code jsonb} on its own — without the cast the insert
     * fails outright. Storing JSON in a {@code jsonb} column rather than
     * {@code text} means malformed JSON is rejected at write time, where the bug
     * is, instead of at read time in a consumer.
     */
    public void insert(OutboxEvent event) {
        jdbcClient
                .sql(INSERT_SQL)
                .param("id", event.id())
                .param("aggregateId", event.aggregateId())
                .param("eventType", event.eventType())
                .param("payload", event.payload())
                .param("createdAt", toOffsetDateTime(event.createdAt()))
                .param("publishedAt", toOffsetDateTime(event.publishedAt()))
                .param("correlationId", event.correlationId())
                .update();
    }

    /**
     * Returns pending events, oldest first, capped at {@code limit}. Takes no
     * locks, so this is for inspection and tests rather than for the relay.
     *
     * <p>Oldest first because events for one account must reach the broker in the
     * order they happened; publishing a settlement before the transaction it
     * settles would be visible to every consumer. The partial index on
     * unpublished rows keeps this scan small no matter how much published history
     * accumulates.
     */
    public List<OutboxEvent> findPending(int limit) {
        return jdbcClient
                .sql(SELECT_PENDING_SQL)
                .param("limit", limit)
                .query(OutboxEventRepository::mapRow)
                .list();
    }

    /**
     * Claims pending events for publication, locking them for the caller's
     * transaction.
     *
     * <p>{@code FOR UPDATE SKIP LOCKED} is what lets several relay instances run
     * at once. {@code FOR UPDATE} alone would make the second instance block until
     * the first commits, turning parallel relays into a queue; {@code SKIP LOCKED}
     * has it walk past the locked rows and take the next free ones instead. Two
     * instances therefore publish different events rather than the same event
     * twice.
     *
     * <p>Must be called inside a transaction — the lock lives and dies with it.
     */
    public List<OutboxEvent> claimPending(int limit) {
        return jdbcClient
                .sql(CLAIM_PENDING_SQL)
                .param("limit", limit)
                .query(OutboxEventRepository::mapRow)
                .list();
    }

    /**
     * Marks an event published.
     *
     * <p>The {@code published_at IS NULL} clause makes this a claim rather than a
     * blind update: if another instance already marked the event, this returns
     * {@code false} instead of silently overwriting a timestamp that is not ours.
     *
     * @return {@code true} if this call marked it, {@code false} if it was already published
     */
    public boolean markPublished(UUID id, Instant publishedAt) {
        int rowsUpdated =
                jdbcClient
                        .sql(MARK_PUBLISHED_SQL)
                        .param("id", id)
                        .param("publishedAt", toOffsetDateTime(publishedAt))
                        .update();

        return rowsUpdated == 1;
    }

    private static OffsetDateTime toOffsetDateTime(Instant instant) {
        // pgjdbc binds OffsetDateTime to TIMESTAMPTZ directly; Instant is not a
        // supported parameter type.
        return instant == null ? null : OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
    }

    private static OutboxEvent mapRow(ResultSet rs, int rowNum) throws SQLException {
        OffsetDateTime publishedAt = rs.getObject("published_at", OffsetDateTime.class);

        return new OutboxEvent(
                rs.getObject("id", UUID.class),
                rs.getObject("aggregate_id", UUID.class),
                rs.getString("event_type"),
                rs.getString("payload"),
                rs.getObject("created_at", OffsetDateTime.class).toInstant(),
                publishedAt == null ? null : publishedAt.toInstant(),
                rs.getString("correlation_id"));
    }
}
