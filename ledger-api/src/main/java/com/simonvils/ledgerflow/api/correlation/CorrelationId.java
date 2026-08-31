package com.simonvils.ledgerflow.api.correlation;

/** Names for the correlation id, shared by the filter, the outbox and the publisher. */
public final class CorrelationId {

    /** Request header a client may set to supply its own id. */
    public static final String HEADER = "X-Correlation-Id";

    /**
     * MDC key, and therefore the field name in the logs.
     *
     * <p>Spring's structured logging copies every MDC entry into the JSON object,
     * so this string is what someone types into a log search during an incident.
     */
    public static final String MDC_KEY = "correlationId";

    /** Kafka header carrying the id to the consumer. */
    public static final String KAFKA_HEADER = "correlation-id";

    private CorrelationId() {}
}
