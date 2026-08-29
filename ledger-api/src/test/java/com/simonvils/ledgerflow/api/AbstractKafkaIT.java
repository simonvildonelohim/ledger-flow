package com.simonvils.ledgerflow.api;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.kafka.KafkaContainer;

/**
 * Base class for integration tests that need PostgreSQL <em>and</em> a Kafka
 * broker.
 *
 * <p>Kept separate from {@link AbstractPostgresIT} so that tests which only touch
 * the database do not pay for a broker they never use. Most of the suite is in
 * that category, and a container that starts for every test class is the fastest
 * way to make an integration suite too slow to run.
 *
 * <p>The broker is a singleton for the same reason as the database: started once
 * in a static initialiser, reused by every subclass, removed by Ryuk when the JVM
 * exits.
 */
public abstract class AbstractKafkaIT extends AbstractPostgresIT {

    // KRaft mode, no ZooKeeper. The apache/kafka image runs KRaft by default,
    // which is what a current deployment looks like.
    static final KafkaContainer KAFKA = new KafkaContainer("apache/kafka:3.8.0");

    static {
        KAFKA.start();
    }

    @DynamicPropertySource
    static void kafkaProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.kafka.bootstrap-servers", KAFKA::getBootstrapServers);
    }
}
