package com.simonvils.ledgerflow.api.messaging;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

/** Topic declarations for the ledger. */
@Configuration
public class KafkaTopicConfiguration {

    /** Topic carrying every accepted transaction. */
    public static final String TRANSACTIONS_TOPIC = "transactions.v1";

    /**
     * Three partitions, so ordering is preserved per account rather than globally.
     *
     * <p>Kafka guarantees order within a partition, not across a topic. Publishing
     * with the account id as the message key sends every event for one account to
     * the same partition, which is the only ordering that matters here: a
     * settlement must not overtake the transaction it settles. Events for
     * different accounts have no relationship, so forcing them into one partition
     * would cap throughput at a single consumer for no benefit.
     *
     * <p>Note the version in the topic name. A change to the payload that breaks
     * consumers ships as {@code transactions.v2} alongside this one, letting
     * consumers migrate on their own schedule instead of all at once.
     *
     * <p>Replication factor 1 because the demo runs a single broker. Production
     * needs at least three with {@code min.insync.replicas=2}; the README records
     * this as a known gap.
     */
    @Bean
    public NewTopic transactionsTopic() {
        return TopicBuilder.name(TRANSACTIONS_TOPIC).partitions(3).replicas(1).build();
    }
}
