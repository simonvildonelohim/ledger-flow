package com.simonvils.ledgerflow.notifier.messaging;

/**
 * Something that reacts to a consumed event.
 *
 * <p>The seam between receiving an event and doing something about it. The
 * consumer's job is to read the topic and turn bytes into a record; what happens
 * next belongs elsewhere, and more than one thing may want to happen. The status
 * projection in issue #29 is one implementation.
 */
public interface TransactionEventHandler {

    void handle(TransactionAcceptedEvent event);
}
