package com.simonvils.ledgerflow.api;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Boots the full Spring context against a real, disposable PostgreSQL
 * container and lets Flyway apply the migration in {@code db/migration}.
 * This is the test that actually proves the schema is valid SQL — the unit
 * test above cannot.
 *
 * <p>Needs a Docker daemon. Runs only under {@code ./mvnw verify}
 * (via the failsafe plugin), never under plain {@code ./mvnw test} — so it
 * runs in GitHub Actions even on a machine that has no Docker installed
 * locally.
 */
@Testcontainers
@SpringBootTest
class LedgerApiApplicationIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Test
    void contextLoadsAndMigrationApplies() {
        // Intentionally empty: a failure to start here means either the
        // context is misconfigured or V1__create_ledger_and_outbox.sql
        // does not apply cleanly to a fresh database.
    }
}
