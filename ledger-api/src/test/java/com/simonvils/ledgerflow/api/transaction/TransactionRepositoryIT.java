package com.simonvils.ledgerflow.api.transaction;

import static org.assertj.core.api.Assertions.assertThat;

import com.simonvils.ledgerflow.api.AbstractPostgresIT;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** Round-trip tests for {@link TransactionRepository} against a real PostgreSQL. */
class TransactionRepositoryIT extends AbstractPostgresIT {

    @Autowired private TransactionRepository repository;

    @Test
    void insertsAndReadsBackEveryField() {
        Transaction inserted = Transaction.accept(uniqueKey(), "acct-001", 125_00L, "CAD");

        assertThat(repository.insertIfAbsent(inserted)).isTrue();
        Optional<Transaction> found = repository.findById(inserted.id());

        assertThat(found).isPresent();
        Transaction transaction = found.orElseThrow();
        assertThat(transaction.id()).isEqualTo(inserted.id());
        assertThat(transaction.idempotencyKey()).isEqualTo(inserted.idempotencyKey());
        assertThat(transaction.accountId()).isEqualTo("acct-001");
        assertThat(transaction.amountMinor()).isEqualTo(125_00L);
        assertThat(transaction.currency()).isEqualTo("CAD");
        assertThat(transaction.status()).isEqualTo(TransactionStatus.PENDING);
        assertThat(transaction.createdAt()).isNotNull();
    }

    @Test
    void preservesLargeAmountsExactly() {
        // A value that a double could not represent exactly. If amounts were ever
        // moved to floating point, this assertion is what would catch it.
        long amount = 9_007_199_254_740_993L;
        Transaction inserted = Transaction.accept(uniqueKey(), "acct-002", amount, "USD");

        repository.insertIfAbsent(inserted);

        assertThat(repository.findById(inserted.id()).orElseThrow().amountMinor()).isEqualTo(amount);
    }

    @Test
    void preservesNegativeAmountsForDebits() {
        Transaction inserted = Transaction.accept(uniqueKey(), "acct-003", -4_250L, "EUR");

        repository.insertIfAbsent(inserted);

        assertThat(repository.findById(inserted.id()).orElseThrow().amountMinor()).isEqualTo(-4_250L);
    }

    @Test
    void doesNotWriteASecondRowForAKeyAlreadyPresent() {
        String key = uniqueKey();
        Transaction first = Transaction.accept(key, "acct-004", 100L, "CAD");
        Transaction second = Transaction.accept(key, "acct-004", 999L, "CAD");
        repository.insertIfAbsent(first);

        boolean written = repository.insertIfAbsent(second);

        assertThat(written).isFalse();
        // The second transaction carries a different id and amount, so finding the
        // first proves nothing was overwritten either.
        assertThat(repository.findByIdempotencyKey(key).orElseThrow().id()).isEqualTo(first.id());
        assertThat(repository.findById(second.id())).isEmpty();
    }

    @Test
    void findsATransactionByItsIdempotencyKey() {
        String key = uniqueKey();
        Transaction inserted = Transaction.accept(key, "acct-005", 7_500L, "CAD");
        repository.insertIfAbsent(inserted);

        assertThat(repository.findByIdempotencyKey(key).orElseThrow().id()).isEqualTo(inserted.id());
    }

    @Test
    void returnsEmptyForAnUnknownId() {
        assertThat(repository.findById(UUID.randomUUID())).isEmpty();
    }

    @Test
    void returnsEmptyForAnUnknownIdempotencyKey() {
        assertThat(repository.findByIdempotencyKey(uniqueKey())).isEmpty();
    }

    /** Tests share one container, so keys must not collide between them. */
    private static String uniqueKey() {
        return "key-" + UUID.randomUUID();
    }
}
