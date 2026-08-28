package com.simonvils.ledgerflow.api.transaction;

import static org.assertj.core.api.Assertions.assertThat;

import com.simonvils.ledgerflow.api.AbstractPostgresIT;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Proves the guarantee that {@code Idempotency-Key} exists to provide: the same
 * key never produces two transactions.
 *
 * <p>The concurrent case is the one that matters. A sequential replay would also
 * pass with a naive read-then-write implementation, which is precisely why a
 * sequential test alone would be misleading — it would report the design as safe
 * while leaving the actual failure mode untested.
 */
class IdempotencyKeyIT extends AbstractPostgresIT {

    private static final int CONCURRENT_REQUESTS = 8;

    @Autowired private TransactionService service;

    @Test
    void aSequentialReplayReturnsTheOriginalTransaction() {
        String key = uniqueKey();
        CreateTransactionRequest request = new CreateTransactionRequest("acct-100", 12_500L, "CAD");

        TransactionAcceptance first = service.accept(key, request);
        TransactionAcceptance second = service.accept(key, request);

        assertThat(first.created()).isTrue();
        assertThat(second.created()).isFalse();
        assertThat(second.transaction().id()).isEqualTo(first.transaction().id());
    }

    @Test
    void aReplayWithADifferentBodyStillReturnsTheOriginal() {
        String key = uniqueKey();
        TransactionAcceptance first =
                service.accept(key, new CreateTransactionRequest("acct-101", 12_500L, "CAD"));

        // Same key, different amount. The key identifies the operation, so the
        // first submission stands and the second body is ignored.
        TransactionAcceptance second =
                service.accept(key, new CreateTransactionRequest("acct-101", 999_999L, "CAD"));

        assertThat(second.created()).isFalse();
        assertThat(second.transaction().id()).isEqualTo(first.transaction().id());
        assertThat(second.transaction().amountMinor()).isEqualTo(12_500L);
    }

    @Test
    void concurrentRequestsWithTheSameKeyCreateExactlyOneTransaction() throws Exception {
        String key = uniqueKey();
        CreateTransactionRequest request = new CreateTransactionRequest("acct-102", 5_000L, "CAD");

        // Every thread blocks on the same latch, so they contend on the insert
        // rather than arriving one after another.
        CountDownLatch startSignal = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(CONCURRENT_REQUESTS);
        List<Future<TransactionAcceptance>> futures = new ArrayList<>();

        try {
            for (int i = 0; i < CONCURRENT_REQUESTS; i++) {
                futures.add(
                        executor.submit(
                                () -> {
                                    startSignal.await();
                                    return service.accept(key, request);
                                }));
            }
            startSignal.countDown();

            List<TransactionAcceptance> results = new ArrayList<>();
            for (Future<TransactionAcceptance> future : futures) {
                results.add(future.get(30, TimeUnit.SECONDS));
            }

            assertThat(results).filteredOn(TransactionAcceptance::created).hasSize(1);

            UUID theOnlyId = results.get(0).transaction().id();
            assertThat(results).extracting(result -> result.transaction().id()).containsOnly(theOnlyId);
        } finally {
            executor.shutdownNow();
        }
    }

    private static String uniqueKey() {
        return "key-" + UUID.randomUUID();
    }
}
