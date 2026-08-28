package com.simonvils.ledgerflow.api.transaction;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** HTTP intake for the ledger. */
@RestController
@RequestMapping("/transactions")
public class TransactionController {

    static final String IDEMPOTENCY_KEY_HEADER = "Idempotency-Key";

    private final TransactionService service;

    public TransactionController(TransactionService service) {
        this.service = service;
    }

    /**
     * Accepts a transaction.
     *
     * <p>The {@code Idempotency-Key} header is mandatory. Making it optional would
     * mean the endpoint is only safe for clients that remember to send it, and the
     * ones that forget are exactly the ones that retry blindly on a timeout.
     *
     * <p>Returns 201 when the transaction was written, 200 when the key had already
     * been used and the original is being returned, 400 when the header is missing
     * or the payload fails validation. Issue #11 replaces the default error body
     * with RFC 9457 problem details.
     */
    @PostMapping
    public ResponseEntity<TransactionResponse> create(
            @RequestHeader(IDEMPOTENCY_KEY_HEADER) @NotBlank String idempotencyKey,
            @Valid @RequestBody CreateTransactionRequest request) {

        TransactionAcceptance acceptance = service.accept(idempotencyKey, request);
        HttpStatus status = acceptance.created() ? HttpStatus.CREATED : HttpStatus.OK;

        return ResponseEntity.status(status).body(TransactionResponse.from(acceptance.transaction()));
    }
}
