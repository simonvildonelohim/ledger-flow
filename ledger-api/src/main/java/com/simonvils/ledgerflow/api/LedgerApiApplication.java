package com.simonvils.ledgerflow.api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point for the ledger-api service: REST intake, ledger persistence,
 * and the outbox relay. See docs/adr/0002 for the outbox design this service
 * implements.
 */
@SpringBootApplication
public class LedgerApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(LedgerApiApplication.class, args);
    }
}
