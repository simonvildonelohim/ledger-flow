package com.simonvils.ledgerflow.api.outbox;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Drives {@link OutboxRelay} on a timer.
 *
 * <p>Split from the relay so that tests can call the relay directly. A test that
 * has to wait for a scheduler tick is slow when it passes and ambiguous when it
 * fails — a failure could mean the logic is wrong or merely that the assertion
 * ran too early.
 *
 * <p>Disabled by default in tests through {@code ledger.outbox.relay.enabled},
 * so a background thread cannot publish the rows a test is about to assert on.
 */
@Component
@EnableScheduling
@ConditionalOnProperty(
        name = "ledger.outbox.relay.enabled",
        havingValue = "true",
        matchIfMissing = true)
public class OutboxRelayScheduler {

    private final OutboxRelay relay;

    public OutboxRelayScheduler(OutboxRelay relay) {
        this.relay = relay;
    }

    /**
     * Polls for pending events.
     *
     * <p>{@code fixedDelay} rather than {@code fixedRate}: the delay is measured
     * from the end of the previous pass, so a slow pass cannot have the next one
     * start on top of it. With {@code fixedRate}, a broker that takes longer than
     * the interval to acknowledge would pile passes up until the pool is
     * exhausted.
     *
     * <p>One second is a deliberate compromise. Publication is asynchronous by
     * design — that is the cost recorded in ADR-0002 — and a shorter interval
     * would mostly add queries that find nothing.
     */
    @Scheduled(fixedDelayString = "${ledger.outbox.relay.interval-ms:1000}")
    public void run() {
        relay.publishPendingEvents();
    }
}
