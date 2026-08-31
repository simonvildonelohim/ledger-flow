package com.simonvils.ledgerflow.notifier.messaging;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.kafka.KafkaContainer;

/**
 * Base class for integration tests in this module.
 *
 * <p>Both containers rather than two base classes: this service does nothing
 * without a broker to read from and a database to record what it has read, so
 * every integration test here needs both. Splitting them would save nothing and
 * leave a base class no test could use.
 *
 * <p>Separate from the equivalent in ledger-api because test classes are not
 * shared between Maven modules, and wiring up a test-jar dependency to share
 * thirty lines would couple two deployables for no gain.
 *
 * <p>Both are singletons: started once in a static initialiser, reused by every
 * subclass, removed by Ryuk when the JVM exits.
 */
@SpringBootTest
public abstract class AbstractIntegrationIT {

    // KRaft mode, no ZooKeeper.
    public static final KafkaContainer KAFKA = new KafkaContainer("apache/kafka:3.8.0");

    public static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine");

    static {
        KAFKA.start();
        POSTGRES.start();
    }

    @DynamicPropertySource
    static void containerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.kafka.bootstrap-servers", KAFKA::getBootstrapServers);
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }
}
