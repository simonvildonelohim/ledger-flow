package com.simonvils.ledgerflow.notifier.messaging;

import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
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

    private static final Logger log = LoggerFactory.getLogger(TransactionEventConsumer.class);

    private final JsonMapper jsonMapper;
    private final List<TransactionEventHandler> handlers;

    public TransactionEventConsumer(JsonMapper jsonMapper, List<TransactionEventHandler> handlers) {
        this.jsonMapper = jsonMapper;
        this.handlers = handlers;
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
     * normally, so a message that throws is redelivered rather than skipped. That
     * is the behaviour we want everywhere except on a payload that will never
     * parse, which is why the two cases are separated below.
     *
     * <p>A malformed message is currently logged and dropped. The production
     * answer is a dead-letter topic; until that exists this is a known gap rather
     * than a solved problem.
     */
    @KafkaListener(topics = TRANSACTIONS_TOPIC)
    public void onMessage(String payload) {
        TransactionAcceptedEvent event;
        try {
            event = jsonMapper.readValue(payload, TransactionAcceptedEvent.class);
        } catch (JacksonException ex) {
            log.error("Dropping a message that cannot be parsed as TransactionAccepted", ex);
            return;
        }

        for (TransactionEventHandler handler : handlers) {
            handler.handle(event);
        }
    }
}
