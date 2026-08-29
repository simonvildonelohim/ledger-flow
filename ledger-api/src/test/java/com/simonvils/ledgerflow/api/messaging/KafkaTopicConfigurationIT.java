package com.simonvils.ledgerflow.api.messaging;

import static org.assertj.core.api.Assertions.assertThat;

import com.simonvils.ledgerflow.api.AbstractKafkaIT;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.TopicDescription;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaAdmin;

/**
 * Proves the broker is reachable and the topic is created on startup, so that a
 * later failure in the relay can be read as a relay problem rather than as
 * missing infrastructure.
 */
class KafkaTopicConfigurationIT extends AbstractKafkaIT {

    @Autowired private KafkaAdmin kafkaAdmin;

    @Test
    void theTransactionsTopicExistsWithItsPartitions() throws Exception {
        try (AdminClient admin = AdminClient.create(kafkaAdmin.getConfigurationProperties())) {
            Set<String> topics = admin.listTopics().names().get(30, TimeUnit.SECONDS);
            assertThat(topics).contains(KafkaTopicConfiguration.TRANSACTIONS_TOPIC);

            TopicDescription description =
                    admin
                            .describeTopics(Set.of(KafkaTopicConfiguration.TRANSACTIONS_TOPIC))
                            .allTopicNames()
                            .get(30, TimeUnit.SECONDS)
                            .get(KafkaTopicConfiguration.TRANSACTIONS_TOPIC);

            // Partitions are what make per-account ordering possible; a topic
            // silently created with one would still pass a naive existence check.
            assertThat(description.partitions()).hasSize(3);
        }
    }
}
