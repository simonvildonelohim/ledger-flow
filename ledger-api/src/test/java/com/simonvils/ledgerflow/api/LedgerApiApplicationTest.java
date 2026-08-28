package com.simonvils.ledgerflow.api;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A placeholder unit test proving the build and test pipeline work end to
 * end before any real domain logic exists. It will be replaced by tests on
 * the intake and idempotency behaviour in M2.
 *
 * <p>This class runs no Spring context and needs no database, so it runs in
 * a second or two on any machine — including one without Docker installed.
 */
class LedgerApiApplicationTest {

    @Test
    void mainClassIsPresent() {
        assertThat(LedgerApiApplication.class).isNotNull();
    }
}
