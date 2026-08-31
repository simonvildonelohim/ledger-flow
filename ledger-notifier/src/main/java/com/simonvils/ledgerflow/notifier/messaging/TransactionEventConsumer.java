package com.simonvils.ledgerflow.notifier.messaging;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

/** Reads the transactions topic and turns each message into a typed event. */
@Component
public class TransactionEventConsumer {

    /**
     * The topic this service reads.
     *
     * <p>Declared here rather than imported from ledger-api. The two modules do
     * not depend on each other, and they should not: a consumer that has to be
     * rebuilt when the producer changes is a consumer that gains nothing from
     * going through a broker.
     */
    public static final String TRANSACTIONS_TOPIC = "transactions.v1";

    /** Header carrying the id this consumer deduplicates on. */
    public static final String EVENT_ID_HEADER = "event-id";

    /** Header carrying the id of the request that produced the event. */
    public static final String CORRELATION_ID_HEADER = "correlation-id";

    /** MDC key, and therefore the field name in the logs. */
    public static final String CORRELATION_ID_MDC_KEY = "correlationId";

    private static final Logger log = LoggerFactory.getLogger(TransactionEventConsumer.class);

    private final JsonMapper jsonMapper;
    private final TransactionEventProcessor processor;

    public TransactionEventConsumer(JsonMapper jsonMapper, TransactionEventProcessor processor) {
        this.jsonMapper = jsonMapper;
        this.processor = processor;
    }

    /**
     * Handles one message.
     *
     * <p>The value arrives as a string and is parsed here rather than by a
     * configured deserializer. A deserializer that fails does so before the
     * listener is ever called, and the container then retries the same message
     * forever — one malformed payload stops every event behind it. Parsing inside
     * the listener means a bad message can be dealt with explicitly.
     *
     * <p>Offsets are committed by the container after this method returns
     * normally, so a message that throws is redelivered rather than skipped.
     *
     * <p>Headers are taken as raw bytes and decoded here. Kafka headers are byte
     * arrays on the wire, and how they surface as anything else depends on
     * header-mapper configuration — a dependency worth avoiding for values this
     * important.
     *
     * <p>The correlation id goes into the MDC before anything is logged and is
     * removed in a finally block. Listener threads are pooled and reused, so an id
     * left behind would attach itself to an unrelated event and send an
     * investigation down the wrong path.
     *
     * <p>A message that cannot be parsed, or that carries no event id, is logged
     * and dropped. Without the id there is no way to tell a retry from a new
     * event, so processing it would risk applying an effect twice. The production
     * answer is a dead-letter topic; until that exists this is a known gap rather
     * than a solved problem.
     */
    @KafkaListener(topics = TRANSACTIONS_TOPIC)
    public void onMessage(
            String payload,
            @Header(name = EVENT_ID_HEADER, required = false) byte[] rawEventId,
            @Header(name = CORRELATION_ID_HEADER, required = false) byte[] rawCorrelationId) {

        if (rawCorrelationId != null) {
            MDC.put(CORRELATION_ID_MDC_KEY, new String(rawCorrelationId, StandardCharsets.UTF_8));
        }

        try {
            UUID eventId = parseEventId(rawEventId);
            if (eventId == null) {
                return;
            }

            TransactionAcceptedEvent event;
            try {
                event = jsonMapper.readValue(payload, TransactionAcceptedEvent.class);
            } catch (JacksonException ex) {
                log.error("Dropping message {}: cannot be parsed as TransactionAccepted", eventId, ex);
                return;
            }

            processor.process(eventId, event);
        } finally {
            MDC.remove(CORRELATION_ID_MDC_KEY);
        }
    }

    private UUID parseEventId(byte[] rawEventId) {
        if (rawEventId == null) {
            log.error("Dropping a message with no {} header", EVENT_ID_HEADER);
            return null;
        }
        try {
            return UUID.fromString(new String(rawEventId, StandardCharsets.UTF_8));
        } catch (IllegalArgumentException ex) {
            log.error("Dropping a message whose {} header is not a UUID", EVENT_ID_HEADER, ex);
            return null;
        }
    }
}
