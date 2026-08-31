-- Ids of events this service has already handled.
--
-- The primary key is the whole mechanism: a redelivered event fails to insert,
-- and that failure is how the consumer recognises it. See ADR-0002 for why
-- redelivery is expected rather than exceptional.
CREATE TABLE processed_event (
    event_id     UUID PRIMARY KEY,
    processed_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
