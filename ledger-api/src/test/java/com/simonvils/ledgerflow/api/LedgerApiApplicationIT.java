package com.simonvils.ledgerflow.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * Verifies that the context starts and that Flyway actually applied the
 * migrations. Asserting on the tables matters: a context-load test alone passes
 * even when no migration ever runs, which is exactly how a missing Flyway
 * auto-configuration went unnoticed until a test first queried a table.
 */
class LedgerApiApplicationIT extends AbstractPostgresIT {

    @Autowired private JdbcClient jdbcClient;

    @Test
    void flywayCreatesTheLedgerAndOutboxTables() {
        List<String> tables =
                jdbcClient
                        .sql("SELECT table_name FROM information_schema.tables WHERE table_schema = 'public'")
                        .query(String.class)
                        .list();

        assertThat(tables).contains("transactions", "outbox_event");
    }
}