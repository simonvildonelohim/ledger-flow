package com.simonvils.ledgerflow.notifier;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LedgerNotifierApplicationTest {

    @Test
    void mainClassIsPresent() {
        assertThat(LedgerNotifierApplication.class).isNotNull();
    }
}
