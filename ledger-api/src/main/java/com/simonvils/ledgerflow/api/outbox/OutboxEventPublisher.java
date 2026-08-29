package com.simonvils.ledgerflow.api.outbox;

import com.simonvils.ledgerflow.api.messaging.KafkaTopicConfiguration;
import java.util.concurrent.TimeUnit;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * Sends outbox events to Kafka and waits for the broker to acknowledge them.
 *
 * <p>Separate from {@link OutboxRelay} so that the decision about what to publish
 * and the mechanics of publishing can be tested apart.
 */
@Component
public class OutboxEventPublisher {

    /**
     * How long to wait for an acknowledgement before treating the send as failed.
     *
     * <p>Bounded on purpose. An unbounded wait would let one unreachable broker
     * hold the relay's thread forever, and since the relay is scheduled on a
     * single thread, that would stop every event rather than just this one.
     */
    private static final long ACK_TIMEOUT_SECONDS = 10;

    private final KafkaTemplate<String, String> kafkaTemplate;

    public OutboxEventPublisher(KafkaTemplate<String, String> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    /**
     * Publishes one event, blocking until the broker acknowledges it.
     *
     * <p>Blocking is the point. {@code KafkaTemplate.send} returns as soon as the
     * message is buffered, long before the broker has it; marking the row
     * published on that basis would lose the event whenever the process died with
     * a full buffer. Waiting for the acknowledgement is what makes the
     * subsequent database update honest.
     *
     * <p>The message key is the aggregate id, so all events for one transaction —
     * and therefore for one account — land on the same partition and keep their
     * order.
     *
     * @throws Exception if the broker does not acknowledge in time, or refuses
     */
    public void publish(OutboxEvent event) throws Exception {
        kafkaTemplate
                .send(
                        KafkaTopicConfiguration.TRANSACTIONS_TOPIC,
                        event.aggregateId().toString(),
                        event.payload())
                .get(ACK_TIMEOUT_SECONDS, TimeUnit.SECONDS);
    }
}
