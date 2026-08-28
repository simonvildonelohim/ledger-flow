package com.simonvils.ledgerflow.api.transaction;

import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Application logic for accepting transactions into the ledger. */
@Service
public class TransactionService {

    private final TransactionRepository repository;

    public TransactionService(TransactionRepository repository) {
        this.repository = repository;
    }

    /**
     * Accepts a transaction and records it as {@link TransactionStatus#PENDING}.
     *
     * <p>The idempotency key is currently generated here, which means every call
     * creates a new transaction and a retried request is charged twice. That is
     * the gap issue #10 closes, by taking the key from the {@code
     * Idempotency-Key} header and returning the original transaction when the
     * database rejects the duplicate.
     *
     * <p>Annotated {@code @Transactional} ahead of that need: from M3 this method
     * writes the ledger row and its outbox row, and those two writes only give
     * the guarantee described in ADR-0002 if they share one transaction.
     */
    @Transactional
    public Transaction accept(CreateTransactionRequest request) {
        Transaction transaction =
                Transaction.accept(
                        UUID.randomUUID().toString(),
                        request.accountId(),
                        request.amountMinor(),
                        request.currency());

        repository.insert(transaction);
        return transaction;
    }
}
