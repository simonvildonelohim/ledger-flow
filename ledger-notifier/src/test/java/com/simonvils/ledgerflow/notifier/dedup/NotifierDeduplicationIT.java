package com.simonvils.ledgerflow.notifier.dedup;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.simonvils.ledgerflow.notifier.messaging.AbstractIntegrationIT;
import com.simonvils.ledgerflow.notifier.messaging.TransactionAcceptedEvent;
import com.simonvils.ledgerflow.notifier.messaging.TransactionEventConsumer;
import com.simonvils.ledgerflow.notifier.messaging.TransactionEventHandler;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.kafka.core.KafkaTemplate;

/**
 * Proves the guarantee that makes at-least-once delivery safe: an event
 * delivered twice has the same effect as an event delivered once.
 *
 * <p>Without this, the relay's design in ADR-0002 would be a bug rather than a
 * trade. It republishes on any crash between the broker's acknowledgement and
 * the database update, so redelivery is not an edge case — it is the normal
 * consequence of a process restarting at the wrong moment.
 */
class NotifierDeduplicationIT extends AbstractIntegrationIT {

    @Autowired private KafkaTemplate<String, String> kafkaTemplate;

    @Autowired private CountingHandler countingHandler;

    @Test
    void theSameEventDeliveredTwiceIsHandledOnce() {
        UUID eventId = UUID.randomUUID();
        UUID transactionId = UUID.randomUUID();
        String payload = payloadFor(transactionId, "acct-600", 12_500L);

        publish(eventId, transactionId, payload);
        await()
                .atMost(Duration.ofSeconds(30))
                .untilAsserted(() -> assertThat(countingHandler.timesHandled(transactionId)).isEqualTo(1));

        // The same event id again, exactly as the relay would republish it after a
        // crash between the broker's acknowledgement and marking the row published.
        publish(eventId, transactionId, payload);

        // Give the second delivery time to be consumed and skipped. Asserting
        // immediately would pass even if deduplication were broken, since the
        // handler would not have run yet either way.
        await()
                .during(Duration.ofSeconds(5))
                .atMost(Duration.ofSeconds(30))
                .untilAsserted(() -> assertThat(countingHandler.timesHandled(transactionId)).isEqualTo(1));
    }

    @Test
    void aDifferentEventForTheSameTransactionIsStillHandled() {
        UUID transactionId = UUID.randomUUID();
        String payload = payloadFor(transactionId, "acct-601", 900L);

        // Two distinct events about one transaction. Deduplicating on the
        // transaction rather than the event would silently drop the second, which
        // is what a settlement or a reversal would look like.
        publish(UUID.randomUUID(), transactionId, payload);
        publish(UUID.randomUUID(), transactionId, payload);

        await()
                .atMost(Duration.ofSeconds(30))
                .untilAsserted(() -> assertThat(countingHandler.timesHandled(transactionId)).isEqualTo(2));
    }

    private static String payloadFor(UUID transactionId, String accountId, long amountMinor) {
        return """
               {"transactionId":"%s","accountId":"%s","amountMinor":%d,"currency":"CAD",\
               "status":"PENDING","acceptedAt":"2026-08-29T12:00:00Z"}\
               """
                .formatted(transactionId, accountId, amountMinor);
    }

    private void publish(UUID eventId, UUID transactionId, String payload) {
        ProducerRecord<String, String> record =
                new ProducerRecord<>(
                        TransactionEventConsumer.TRANSACTIONS_TOPIC, transactionId.toString(), payload);
        record
                .headers()
                .add(
                        TransactionEventConsumer.EVENT_ID_HEADER,
                        eventId.toString().getBytes(StandardCharsets.UTF_8));
        kafkaTemplate.send(record);
    }

    @TestConfiguration
    static class CountingHandlerConfiguration {

        @Bean
        CountingHandler countingHandler() {
            return new CountingHandler();
        }
    }

    static class CountingHandler implements TransactionEventHandler {

        private final List<UUID> handled = new CopyOnWriteArrayList<>();

        @Override
        public void handle(TransactionAcceptedEvent event) {
            handled.add(event.transactionId());
        }

        long timesHandled(UUID transactionId) {
            return handled.stream().filter(transactionId::equals).count();
        }
    }
}
