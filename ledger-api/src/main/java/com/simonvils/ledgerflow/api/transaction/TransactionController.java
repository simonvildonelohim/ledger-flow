package com.simonvils.ledgerflow.api.transaction;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** HTTP intake for the ledger. */
@RestController
@RequestMapping("/transactions")
public class TransactionController {

    private final TransactionService service;

    public TransactionController(TransactionService service) {
        this.service = service;
    }

    /**
     * Accepts a transaction.
     *
     * <p>Returns 201 with the created resource, or 400 when the payload fails
     * validation. Issue #11 replaces the default error body with RFC 9457
     * problem details; until then Spring's own representation applies.
     */
    @PostMapping
    public ResponseEntity<TransactionResponse> create(
            @Valid @RequestBody CreateTransactionRequest request) {
        Transaction accepted = service.accept(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(TransactionResponse.from(accepted));
    }
}
