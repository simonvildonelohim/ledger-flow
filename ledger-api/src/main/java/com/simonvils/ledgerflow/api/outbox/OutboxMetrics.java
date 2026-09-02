package com.simonvils.ledgerflow.api.outbox;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

/**
 * Publishes the depth of the outbox as a metric.
 *
 * <p>This is the health of ADR-0002 made visible. Everything else about the
 * pattern can look fine while events pile up unpublished: the API keeps
 * returning 201, the database keeps committing, and nothing logs an error,
 * because a relay that cannot reach the broker is behaving exactly as designed.
 * The only signal that anything is wrong is that this number climbs and does
 * not come back down.
 *
 * <p>Registered as a gauge rather than a counter because it measures a level,
 * not a total: it goes down as well as up, and its value at any moment is the
 * answer, not the sum of what came before.
 */
@Component
public class OutboxMetrics {

    /** Metric name; queried in the dashboard as {@code ledger_outbox_pending_events}. */
    public static final String PENDING_GAUGE = "ledger.outbox.pending.events";

    public OutboxMetrics(OutboxEventRepository repository, MeterRegistry registry) {
        // The gauge holds a weak reference to the repository and reads it on
        // demand, so the value is whatever the table says at scrape time rather
        // than a number this class has to keep in step.
        Gauge.builder(PENDING_GAUGE, repository, OutboxEventRepository::countPending)
                .description("Outbox events written but not yet published to the broker")
                .register(registry);
    }
}