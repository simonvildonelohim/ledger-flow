package com.simonvils.ledgerflow.notifier.messaging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Records every consumed event in the log.
 *
 * <p>Not a placeholder. A line per event is what makes it possible to answer
 * "did this transaction reach the consumer, and when?" without attaching a
 * debugger — and that question is the first one asked during an incident.
 */
@Component
public class LoggingTransactionEventHandler implements TransactionEventHandler {

    private static final Logger log = LoggerFactory.getLogger(LoggingTransactionEventHandler.class);

    @Override
    public void handle(TransactionAcceptedEvent event) {
        log.info(
                "Consumed TransactionAccepted transactionId={} accountId={} amountMinor={} currency={}",
                event.transactionId(),
                event.accountId(),
                event.amountMinor(),
                event.currency());
    }
}
