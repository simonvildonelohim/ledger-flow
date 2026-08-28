package com.simonvils.ledgerflow.api;

import org.junit.jupiter.api.Test;

/**
 * Boots the full Spring context against a real PostgreSQL and lets Flyway apply
 * the migrations. This is the test that proves the schema is valid SQL — the
 * unit tests cannot.
 *
 * <p>Needs a Docker daemon, so it runs under {@code ./mvnw verify} (failsafe)
 * rather than {@code ./mvnw test}, which keeps the local unit-test loop fast on
 * machines without Docker installed.
 */
class LedgerApiApplicationIT extends AbstractPostgresIT {

    @Test
    void contextLoadsAndMigrationsApply() {
        // Intentionally empty: failing to start here means either the context is
        // misconfigured or a migration does not apply cleanly to a fresh database.
    }
}
