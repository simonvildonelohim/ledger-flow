package com.simonvils.ledgerflow.api.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;

import com.simonvils.ledgerflow.api.AbstractPostgresIT;
import com.simonvils.ledgerflow.api.transaction.CreateTransactionRequest;
import com.simonvils.ledgerflow.api.transaction.TransactionAcceptance;
import com.simonvils.ledgerflow.api.transaction.TransactionService;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

/**
 * Proves the guarantee that justifies the whole pattern: a broker that is not
 * there delays events, it does not lose them.
 *
 * <p>The outage is simulated at the publisher rather than by stopping the real
 * container, which is shared with every other test in the run. What is under
 * test here is the relay's response to a publication failure — that it leaves
 * the row pending, and that a later pass picks it up — and that behaviour is the
 * same whichever way the send fails.
 *
 * <p>No Kafka container is needed, since nothing is expected to reach a broker.
 */
@TestPropertySource(properties = "ledger.outbox.relay.enabled=false")
class BrokerOutageIT extends AbstractPostgresIT {

    @Autowired private TransactionService service;

    @Autowired private OutboxRelay relay;

    @Autowired private OutboxEventRepository outbox;

    @MockitoSpyBean private OutboxEventPublisher publisher;

    @Test
    void anEventSurvivesTheBrokerBeingUnavailable() throws Exception {
        doThrow(new IllegalStateException("broker unavailable")).when(publisher).publish(any());
        TransactionAcceptance acceptance =
                service.accept(uniqueKey(), new CreateTransactionRequest("acct-400", 7_500L, "CAD"));
        UUID transactionId = acceptance.transaction().id();

        int publishedDuringOutage = relay.publishPendingEvents();

        assertThat(publishedDuringOutage).isZero();
        // The transaction was accepted and the client already has its 201. If the
        // event were dropped here, nothing downstream would ever know about it.
        assertThat(pendingFor(transactionId)).isTrue();

        // The broker comes back.
        doNothing().when(publisher).publish(any());
        int publishedAfterRecovery = relay.publishPendingEvents();

        assertThat(publishedAfterRecovery).isPositive();
        assertThat(pendingFor(transactionId)).isFalse();
    }

    @Test
    void aFailedEventDoesNotLetLaterOnesOvertakeIt() throws Exception {
        doThrow(new IllegalStateException("broker unavailable")).when(publisher).publish(any());
        TransactionAcceptance first =
                service.accept(uniqueKey(), new CreateTransactionRequest("acct-401", 100L, "CAD"));
        TransactionAcceptance second =
                service.accept(uniqueKey(), new CreateTransactionRequest("acct-401", 200L, "CAD"));

        relay.publishPendingEvents();

        // Skipping the failed event and publishing the next would let a later
        // event for the same account arrive first, which is exactly the ordering
        // consumers rely on not happening.
        assertThat(pendingFor(first.transaction().id())).isTrue();
        assertThat(pendingFor(second.transaction().id())).isTrue();
    }

    private boolean pendingFor(UUID transactionId) {
        return outbox.findPending(1000).stream()
                .anyMatch(event -> event.aggregateId().equals(transactionId));
    }

    private static String uniqueKey() {
        return "key-" + UUID.randomUUID();
    }
}
