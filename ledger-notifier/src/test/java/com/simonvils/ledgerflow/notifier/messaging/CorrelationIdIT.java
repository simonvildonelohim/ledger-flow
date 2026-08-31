package com.simonvils.ledgerflow.notifier.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.kafka.core.KafkaTemplate;

/**
 * Proves the correlation id survives the broker and is back in the MDC by the
 * time an event is handled.
 *
 * <p>This is the half of the round trip that happens in this service. The other
 * half — request to outbox row to Kafka header — is covered in ledger-api. Until
 * both run under one compose file, the two halves meet by inspection.
 */
class CorrelationIdIT extends AbstractIntegrationIT {

    @Autowired private KafkaTemplate<String, String> kafkaTemplate;

    @Autowired private MdcCapturingHandler handler;

    @Test
    void theCorrelationIdIsInTheMdcWhileTheEventIsHandled() {
        UUID transactionId = UUID.randomUUID();
        String correlationId = "req-" + UUID.randomUUID();

        publish(transactionId, correlationId);

        // What is asserted is the MDC, not the header: a value that arrives but
        // never reaches the MDC would appear nowhere in the logs, which is the
        // only place it is any use.
        await()
                .atMost(Duration.ofSeconds(30))
                .untilAsserted(
                        () ->
                                assertThat(handler.seenByTransaction.get(transactionId))
                                        .isEqualTo(correlationId));
    }

    @Test
    void anEventWithNoCorrelationIdIsStillHandled() {
        UUID transactionId = UUID.randomUUID();

        // Rows written before the column existed carry no id. Dropping those
        // events would turn a missing log field into lost data.
        publish(transactionId, null);

        await()
                .atMost(Duration.ofSeconds(30))
                .untilAsserted(
                        () -> assertThat(handler.seenByTransaction).containsKey(transactionId));
    }

    private void publish(UUID transactionId, String correlationId) {
        String payload =
                """
                {"transactionId":"%s","accountId":"acct-800","amountMinor":100,"currency":"CAD",\
                "status":"PENDING","acceptedAt":"2026-08-31T12:00:00Z"}\
                """
                        .formatted(transactionId);

        ProducerRecord<String, String> record =
                new ProducerRecord<>(
                        TransactionEventConsumer.TRANSACTIONS_TOPIC, transactionId.toString(), payload);
        record
                .headers()
                .add(
                        TransactionEventConsumer.EVENT_ID_HEADER,
                        UUID.randomUUID().toString().getBytes(StandardCharsets.UTF_8));
        if (correlationId != null) {
            record
                    .headers()
                    .add(
                            TransactionEventConsumer.CORRELATION_ID_HEADER,
                            correlationId.getBytes(StandardCharsets.UTF_8));
        }
        kafkaTemplate.send(record);
    }

    @TestConfiguration
    static class MdcCapturingHandlerConfiguration {

        @Bean
        MdcCapturingHandler mdcCapturingHandler() {
            return new MdcCapturingHandler();
        }
    }

    /** Records what the MDC held at the moment each event was handled. */
    static class MdcCapturingHandler implements TransactionEventHandler {

        final Map<UUID, String> seenByTransaction = new ConcurrentHashMap<>();

        @Override
        public void handle(TransactionAcceptedEvent event) {
            String fromMdc = MDC.get(TransactionEventConsumer.CORRELATION_ID_MDC_KEY);
            // ConcurrentHashMap rejects null values, so absence is recorded
            // explicitly rather than by omission.
            seenByTransaction.put(event.transactionId(), fromMdc == null ? "" : fromMdc);
        }
    }
}
