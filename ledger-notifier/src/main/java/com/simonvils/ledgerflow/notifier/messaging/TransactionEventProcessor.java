package com.simonvils.ledgerflow.notifier.messaging;

import com.simonvils.ledgerflow.notifier.dedup.ProcessedEventRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Applies an event once, however many times it is delivered.
 *
 * <p>This is the counterpart to the relay's at-least-once publication. The relay
 * marks an outbox row published only after the broker acknowledges it, so a crash
 * in between republishes rather than loses the event — a trade ADR-0002 makes
 * deliberately. It is only a sound trade because of this class.
 */
@Component
public class TransactionEventProcessor {

    private static final Logger log = LoggerFactory.getLogger(TransactionEventProcessor.class);

    private final ProcessedEventRepository processedEvents;
    private final List<TransactionEventHandler> handlers;

    public TransactionEventProcessor(
            ProcessedEventRepository processedEvents, List<TransactionEventHandler> handlers) {
        this.processedEvents = processedEvents;
        this.handlers = handlers;
    }

    /**
     * Processes an event unless it has already been processed.
     *
     * <p>The claim and the handlers share one transaction, and that is the whole
     * design. If the id were committed separately and the handlers then failed,
     * the event would be marked done without its effect ever happening, and the
     * redelivery that would have fixed it gets skipped — a silent, permanent loss.
     * Committing together means either both happened or neither did, and neither
     * is safe to retry.
     *
     * <p>A skip is logged at debug rather than warn. Redelivery is normal
     * behaviour for an at-least-once pipeline, and logging it as a problem would
     * train whoever reads these logs to ignore them.
     */
    @Transactional
    public void process(UUID eventId, TransactionAcceptedEvent event) {
        if (!processedEvents.claim(eventId, Instant.now())) {
            log.debug(
                    "Skipping event {} for transaction {}: already processed",
                    eventId,
                    event.transactionId());
            return;
        }

        for (TransactionEventHandler handler : handlers) {
            handler.handle(event);
        }
    }
}
