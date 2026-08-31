package com.simonvils.ledgerflow.notifier.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.kafka.core.KafkaTemplate;

/**
 * Proves a message published to the topic reaches this service and is parsed
 * into a typed event.
 *
 * <p>Observed through an extra handler rather than by mocking the consumer. The
 * handler list is a real extension point, so a test that plugs into it exercises
 * the same path production code uses instead of a path that only exists under
 * test.
 */
class TransactionEventConsumerIT extends AbstractIntegrationIT {

    @Autowired private KafkaTemplate<String, String> kafkaTemplate;

    @Autowired private CapturingHandler capturingHandler;

    @Test
    void receivesAndParsesAPublishedEvent() {
        UUID transactionId = UUID.randomUUID();
        String payload =
                """
                {"transactionId":"%s","accountId":"acct-500","amountMinor":12500,\
                "currency":"CAD","status":"PENDING","acceptedAt":"2026-08-29T12:00:00Z"}\
                """
                        .formatted(transactionId);

        publish(UUID.randomUUID(), transactionId, payload);

        await()
                .atMost(Duration.ofSeconds(30))
                .untilAsserted(
                        () -> {
                            TransactionAcceptedEvent event = findByTransactionId(transactionId);
                            assertThat(event).isNotNull();
                            assertThat(event.accountId()).isEqualTo("acct-500");
                            assertThat(event.amountMinor()).isEqualTo(12_500L);
                            assertThat(event.currency()).isEqualTo("CAD");
                            assertThat(event.status()).isEqualTo("PENDING");
                            assertThat(event.acceptedAt()).isEqualTo(Instant.parse("2026-08-29T12:00:00Z"));
                        });
    }

    @Test
    void ignoresAFieldItDoesNotKnowAbout() {
        UUID transactionId = UUID.randomUUID();
        // A producer adding a field must not break an older consumer, otherwise
        // every payload change becomes a coordinated deployment.
        String payload =
                """
                {"transactionId":"%s","accountId":"acct-501","amountMinor":900,\
                "currency":"CAD","status":"PENDING","acceptedAt":"2026-08-29T12:00:00Z",\
                "somethingAddedLater":"whatever"}\
                """
                        .formatted(transactionId);

        publish(UUID.randomUUID(), transactionId, payload);

        await()
                .atMost(Duration.ofSeconds(30))
                .untilAsserted(() -> assertThat(findByTransactionId(transactionId)).isNotNull());
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

    private TransactionAcceptedEvent findByTransactionId(UUID transactionId) {
        return capturingHandler.received.stream()
                .filter(event -> event.transactionId().equals(transactionId))
                .findFirst()
                .orElse(null);
    }

    @TestConfiguration
    static class CapturingHandlerConfiguration {

        @Bean
        CapturingHandler capturingHandler() {
            return new CapturingHandler();
        }
    }

    static class CapturingHandler implements TransactionEventHandler {

        final BlockingQueue<TransactionAcceptedEvent> received = new LinkedBlockingQueue<>();

        @Override
        public void handle(TransactionAcceptedEvent event) {
            received.add(event);
        }
    }
}
