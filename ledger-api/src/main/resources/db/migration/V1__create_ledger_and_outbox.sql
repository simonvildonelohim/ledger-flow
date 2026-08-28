-- Core ledger: one row per accepted transaction.
-- Amounts are stored in minor units (cents) as BIGINT, never floating point.
CREATE TABLE transactions (
    id              UUID PRIMARY KEY,
    idempotency_key VARCHAR(255) NOT NULL,
    account_id      VARCHAR(255) NOT NULL,
    amount_minor    BIGINT NOT NULL,
    currency        CHAR(3) NOT NULL,
    status          VARCHAR(32) NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT uq_transactions_idempotency_key UNIQUE (idempotency_key)
);

-- Transactional outbox: written in the same DB transaction as the ledger row
-- it describes. A relay polls unpublished rows and publishes them to Kafka.
-- See docs/adr/0002-transactional-outbox-for-event-publication.md.
CREATE TABLE outbox_event (
    id              UUID PRIMARY KEY,
    aggregate_id    UUID NOT NULL,
    event_type      VARCHAR(255) NOT NULL,
    payload         JSONB NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    published_at    TIMESTAMPTZ,

    CONSTRAINT fk_outbox_event_transaction
        FOREIGN KEY (aggregate_id) REFERENCES transactions (id)
);

-- Partial index: the relay only ever scans unpublished rows, and this index
-- stays small regardless of how large the published history grows.
CREATE INDEX idx_outbox_event_unpublished
    ON outbox_event (created_at)
    WHERE published_at IS NULL;
