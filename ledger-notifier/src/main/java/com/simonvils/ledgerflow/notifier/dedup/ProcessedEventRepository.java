package com.simonvils.ledgerflow.notifier.dedup;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** Record of which events this service has already handled. */
@Repository
public class ProcessedEventRepository {

    private static final String CLAIM_SQL =
            """
            INSERT INTO processed_event (event_id, processed_at)
            VALUES (:eventId, :processedAt)
            ON CONFLICT (event_id) DO NOTHING
            """;

    private final JdbcClient jdbcClient;

    public ProcessedEventRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    /**
     * Claims an event for processing.
     *
     * <p>The same shape as the outbox insert in ledger-api, for the same reason:
     * a check followed by an insert does not survive concurrency. Two consumer
     * instances handed the same redelivered message can both find the id absent
     * before either writes it, and both would then apply the effect. The primary
     * key is the only thing that actually decides, and {@code ON CONFLICT DO
     * NOTHING} lets the loser find out without aborting its transaction — which
     * matters, because that transaction also carries the effect.
     *
     * @return {@code true} if this call claimed the event, {@code false} if it had
     *     already been handled
     */
    public boolean claim(UUID eventId, Instant processedAt) {
        int rowsWritten =
                jdbcClient
                        .sql(CLAIM_SQL)
                        .param("eventId", eventId)
                        .param("processedAt", OffsetDateTime.ofInstant(processedAt, ZoneOffset.UTC))
                        .update();

        return rowsWritten == 1;
    }
}
