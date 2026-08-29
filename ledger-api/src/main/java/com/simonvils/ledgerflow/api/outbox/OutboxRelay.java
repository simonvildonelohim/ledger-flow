package com.simonvils.ledgerflow.api.outbox;

import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Moves pending outbox events to the broker.
 *
 * <p>This is the second half of the pattern in ADR-0002. The first half
 * guarantees an event exists for every accepted transaction; this one guarantees
 * it eventually reaches Kafka.
 *
 * <p>Scheduling is not this class's concern — {@link OutboxRelayScheduler} calls
 * {@link #publishPendingEvents()} on a timer. Keeping them apart means the relay
 * can be driven directly from a test without waiting for a scheduler tick.
 */
@Component
public class OutboxRelay {

    private static final Logger log = LoggerFactory.getLogger(OutboxRelay.class);

    /**
     * How many events one pass handles.
     *
     * <p>Bounded so a large backlog is drained over several passes instead of in
     * one transaction that holds locks for minutes and rolls back entirely if the
     * broker fails near the end.
     */
    private static final int BATCH_SIZE = 100;

    private final OutboxEventRepository repository;
    private final OutboxEventPublisher publisher;

    public OutboxRelay(OutboxEventRepository repository, OutboxEventPublisher publisher) {
        this.repository = repository;
        this.publisher = publisher;
    }

    /**
     * Publishes one batch of pending events, oldest first.
     *
     * <p>Runs in a transaction so that {@code FOR UPDATE SKIP LOCKED} in
     * {@link OutboxEventRepository#claimPending(int)} holds its locks for the
     * duration: another instance running at the same time will skip these rows
     * and take different ones.
     *
     * <p>An event is marked published only after the broker acknowledges it. If
     * the process dies between the acknowledgement and the update, the row stays
     * pending and is published again on the next pass — the event is delivered
     * twice rather than lost, which is the trade this design makes deliberately.
     * Consumers deduplicate on the event id, which is what makes that trade safe.
     *
     * <p>A failure stops the batch rather than skipping past it. Continuing would
     * publish a later event for the same account before an earlier one, and
     * consumers would see a settlement arrive before the transaction it settles.
     * The remaining events keep their place and go out on the next pass.
     *
     * @return how many events were published in this pass
     */
    @Transactional
    public int publishPendingEvents() {
        List<OutboxEvent> claimed = repository.claimPending(BATCH_SIZE);
        if (claimed.isEmpty()) {
            return 0;
        }

        int published = 0;
        for (OutboxEvent event : claimed) {
            try {
                publisher.publish(event);
            } catch (Exception ex) {
                // Not an error to alert on by itself: a broker that is briefly
                // unavailable is exactly the case the outbox exists to absorb.
                log.warn(
                        "Stopping this outbox pass after {} of {} events; {} could not be published",
                        published,
                        claimed.size(),
                        event.id(),
                        ex);
                break;
            }

            repository.markPublished(event.id(), Instant.now());
            published++;
        }

        return published;
    }
}
