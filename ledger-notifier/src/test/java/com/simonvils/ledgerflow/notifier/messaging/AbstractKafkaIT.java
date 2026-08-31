package com.simonvils.ledgerflow.notifier.messaging;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.kafka.KafkaContainer;

/**
 * Base class for integration tests in this module that need a broker.
 *
 * <p>A separate declaration from the one in ledger-api because test classes are
 * not shared between Maven modules, and wiring up a test-jar dependency to share
 * thirty lines would couple two deployables for no gain.
 *
 * <p>Singleton container: started once in a static initialiser, reused by every
 * subclass, removed by Ryuk when the JVM exits.
 */
@SpringBootTest
public abstract class AbstractKafkaIT {

    // KRaft mode, no ZooKeeper.
    public static final KafkaContainer KAFKA = new KafkaContainer("apache/kafka:3.8.0");

    static {
        KAFKA.start();
    }

    @DynamicPropertySource
    static void kafkaProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.kafka.bootstrap-servers", KAFKA::getBootstrapServers);
    }
}
