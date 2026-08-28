package com.simonvils.ledgerflow.notifier;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point for the ledger-notifier service. Currently a bare skeleton
 * proving the module builds and boots; the Kafka consumer and deduplication
 * logic land in M4 (see the roadmap in the root README).
 */
@SpringBootApplication
public class LedgerNotifierApplication {

    public static void main(String[] args) {
        SpringApplication.run(LedgerNotifierApplication.class, args);
    }
}
