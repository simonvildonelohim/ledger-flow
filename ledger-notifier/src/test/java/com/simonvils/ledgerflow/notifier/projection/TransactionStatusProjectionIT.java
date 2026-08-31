package com.simonvils.ledgerflow.notifier.projection;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.simonvils.ledgerflow.notifier.messaging.AbstractIntegrationIT;
import com.simonvils.ledgerflow.notifier.messaging.TransactionEventConsumer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;

/**
 * Covers the consumer half of the path: a published event becomes a queryable
 * status.
 *
 * <p>The producer half — POST, ledger write, outbox, relay — is proven by the
 * tests in ledger-api. Nothing yet exercises both services in one run, so the
 * two agree on the payload by inspection rather than by test. That gap closes
 * when the full stack runs under one compose file.
 */
class TransactionStatusProjectionIT extends AbstractIntegrationIT {

    @Autowired private KafkaTemplate<String, String> kafkaTemplate;

    @Autowired private TransactionStatusRepository repository;

    @Test
    void aConsumedEventBecomesAQueryableStatus() {
        UUID transactionId = UUID.randomUUID();

        publish(UUID.randomUUID(), transactionId, payloadFor(transactionId, "acct-700", 12_500L));

        await()
                .atMost(Duration.ofSeconds(30))
                .untilAsserted(
                        () -> {
                            TransactionStatusView view =
                                    repository.findByTransactionId(transactionId).orElse(null);
                            assertThat(view).isNotNull();
                            assertThat(view.status()).isEqualTo(TransactionStatusProjection.SETTLED);
                            assertThat(view.accountId()).isEqualTo("acct-700");
                            assertThat(view.amountMinor()).isEqualTo(12_500L);
                            assertThat(view.currency()).isEqualTo("CAD");
                            assertThat(view.acceptedAt()).isEqualTo(Instant.parse("2026-08-29T12:00:00Z"));
                        });
    }

    @Test
    void applyingTheSameEventTwiceLeavesOneRow() {
        UUID transactionId = UUID.randomUUID();
        TransactionStatusView view =
                new TransactionStatusView(
                        transactionId,
                        "acct-701",
                        900L,
                        "CAD",
                        TransactionStatusProjection.SETTLED,
                        Instant.parse("2026-08-29T12:00:00Z"),
                        Instant.now());

        // Called directly, bypassing deduplication. The projection has to be
        // idempotent by itself: relying on the consumer to never call it twice
        // would make it correct only for as long as that stays true.
        repository.save(view);
        repository.save(view);

        assertThat(repository.findByTransactionId(transactionId)).isPresent();
        assertThat(repository.findByTransactionId(transactionId).orElseThrow().status())
                .isEqualTo(TransactionStatusProjection.SETTLED);
    }

    @Test
    void aTransactionThisServiceHasNotSeenHasNoStatus() {
        assertThat(repository.findByTransactionId(UUID.randomUUID())).isEmpty();
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
}
