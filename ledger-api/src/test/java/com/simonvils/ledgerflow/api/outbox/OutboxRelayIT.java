package com.simonvils.ledgerflow.api.outbox;

import static org.assertj.core.api.Assertions.assertThat;

import com.simonvils.ledgerflow.api.AbstractKafkaIT;
import com.simonvils.ledgerflow.api.messaging.KafkaTopicConfiguration;
import com.simonvils.ledgerflow.api.transaction.CreateTransactionRequest;
import com.simonvils.ledgerflow.api.transaction.TransactionAcceptance;
import com.simonvils.ledgerflow.api.transaction.TransactionService;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;

/**
 * End-to-end check that an accepted transaction reaches the broker.
 *
 * <p>The scheduler is switched off so the relay runs only when this test says so.
 * Otherwise a background pass could publish the event before the assertions run,
 * and the test would pass for the wrong reason.
 */
@TestPropertySource(properties = "ledger.outbox.relay.enabled=false")
class OutboxRelayIT extends AbstractKafkaIT {

    @Autowired private TransactionService service;

    @Autowired private OutboxRelay relay;

    @Autowired private OutboxEventRepository outbox;

    @Test
    void anAcceptedTransactionReachesTheBrokerAndIsMarkedPublished() {
        TransactionAcceptance acceptance =
                service.accept(uniqueKey(), new CreateTransactionRequest("acct-300", 12_500L, "CAD"));
        UUID transactionId = acceptance.transaction().id();

        drainAllPasses();

        assertThat(readTopic())
                .anySatisfy(
                        record -> {
                            // The key is what keeps one account's events on one
                            // partition, and therefore in order.
                            assertThat(record.key()).isEqualTo(transactionId.toString());
                            assertThat(record.value()).contains(transactionId.toString());
                            assertThat(record.value()).contains("acct-300");
                            assertThat(record.value()).contains("12500");
                        });

        assertThat(outbox.findPending(1000))
                .extracting(OutboxEvent::aggregateId)
                .doesNotContain(transactionId);
    }

    @Test
    void aPassOverAnEmptyOutboxPublishesNothing() {
        service.accept(uniqueKey(), new CreateTransactionRequest("acct-301", 900L, "CAD"));

        drainAllPasses();

        assertThat(relay.publishPendingEvents()).isZero();
    }

    /** Runs passes until the outbox is empty, since one pass is batch-limited. */
    private void drainAllPasses() {
        while (relay.publishPendingEvents() > 0) {
            // keep going
        }
    }

    private List<ConsumerRecord<String, String>> readTopic() {
        Map<String, Object> config =
                Map.of(
                        ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers(),
                        ConsumerConfig.GROUP_ID_CONFIG, "test-" + UUID.randomUUID(),
                        ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest",
                        ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class,
                        ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);

        List<ConsumerRecord<String, String>> records = new ArrayList<>();
        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(config)) {
            consumer.subscribe(List.of(KafkaTopicConfiguration.TRANSACTIONS_TOPIC));
            // The first poll usually returns nothing while the group is still
            // being assigned its partitions, so poll a few times before giving up.
            for (int attempt = 0; attempt < 5 && records.isEmpty(); attempt++) {
                consumer.poll(Duration.ofSeconds(5)).forEach(records::add);
            }
        }
        return records;
    }

    private static String uniqueKey() {
        return "key-" + UUID.randomUUID();
    }
}
