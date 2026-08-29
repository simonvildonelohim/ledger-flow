package com.simonvils.ledgerflow.api.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;

import com.simonvils.ledgerflow.api.AbstractPostgresIT;
import com.simonvils.ledgerflow.api.transaction.CreateTransactionRequest;
import com.simonvils.ledgerflow.api.transaction.TransactionAcceptance;
import com.simonvils.ledgerflow.api.transaction.TransactionRepository;
import com.simonvils.ledgerflow.api.transaction.TransactionService;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

/**
 * Proves the guarantee ADR-0002 is built around: a transaction and its event are
 * written together or not at all.
 *
 * <p>The failure case is the one that matters. A test that only checks both rows
 * appear after a successful call would pass just as happily with two separate
 * commits, which is the design this project exists to avoid.
 */
class OutboxAtomicityIT extends AbstractPostgresIT {

    @Autowired private TransactionService service;

    @Autowired private TransactionRepository transactions;

    /**
     * A spy rather than a mock: every other test here needs the real repository,
     * and only one method in one test is stubbed to fail.
     */
    @MockitoSpyBean private OutboxEventRepository outbox;

    @Test
    void acceptingATransactionWritesItsEvent() {
        String key = uniqueKey();

        TransactionAcceptance acceptance =
                service.accept(key, new CreateTransactionRequest("acct-200", 12_500L, "CAD"));

        List<OutboxEvent> events = eventsFor(acceptance.transaction().id());
        assertThat(events).hasSize(1);
        OutboxEvent event = events.getFirst();
        assertThat(event.eventType()).isEqualTo(TransactionService.TRANSACTION_ACCEPTED);
        assertThat(event.isPending()).isTrue();
        assertThat(event.payload())
                .contains(acceptance.transaction().id().toString())
                .contains("acct-200")
                .contains("12500")
                .contains("CAD")
                .contains("PENDING");
        // The caller's key is not part of the published contract.
        assertThat(event.payload()).doesNotContain(key);
    }

    @Test
    void aFailureWritingTheEventRollsBackTheLedgerRow() {
        doThrow(new RuntimeException("broker-side failure while writing the outbox"))
                .when(outbox)
                .insert(any());
        String key = uniqueKey();

        assertThatThrownBy(
                        () -> service.accept(key, new CreateTransactionRequest("acct-201", 900L, "CAD")))
                .isInstanceOf(RuntimeException.class);

        // Without a shared transaction the ledger row would have survived, leaving
        // an accepted transaction no consumer will ever hear about.
        assertThat(transactions.findByIdempotencyKey(key)).isEmpty();
    }

    @Test
    void aReplayDoesNotWriteASecondEvent() {
        String key = uniqueKey();
        CreateTransactionRequest request = new CreateTransactionRequest("acct-202", 3_000L, "CAD");
        TransactionAcceptance first = service.accept(key, request);

        TransactionAcceptance replay = service.accept(key, request);

        assertThat(replay.created()).isFalse();
        // A second event would tell every consumer the transaction happened twice.
        assertThat(eventsFor(first.transaction().id())).hasSize(1);
    }

    private List<OutboxEvent> eventsFor(UUID transactionId) {
        return outbox.findPending(1000).stream()
                .filter(event -> event.aggregateId().equals(transactionId))
                .toList();
    }

    private static String uniqueKey() {
        return "key-" + UUID.randomUUID();
    }
}
