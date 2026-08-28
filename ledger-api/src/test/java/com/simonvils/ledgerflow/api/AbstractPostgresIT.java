package com.simonvils.ledgerflow.api;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Base class for integration tests that need a real PostgreSQL.
 *
 * <p>The container is a singleton: started once in a static initialiser and
 * reused by every subclass for the lifetime of the JVM, rather than started and
 * stopped per test class. With one container per class, each new integration
 * test would add roughly ten seconds of startup to the build — a cost that grows
 * with every test written and quietly discourages writing them. Testcontainers'
 * Ryuk sidecar removes the container when the JVM exits.
 *
 * <p>Flyway applies the migrations in {@code db/migration} on context startup,
 * so tests run against the same schema as production.
 */
@SpringBootTest
public abstract class AbstractPostgresIT {

    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    static {
        POSTGRES.start();
    }

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }
}
