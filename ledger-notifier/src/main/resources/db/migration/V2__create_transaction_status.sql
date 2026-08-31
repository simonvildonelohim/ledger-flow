-- Status of each transaction, as this service understands it.
--
-- A projection, not a copy of the ledger: ledger-api owns the transaction, this
-- table owns the answer to "where is it now?". See ADR-0004 for why the two live
-- in different databases.
--
-- The fields duplicated from the event are here so that a status query needs no
-- second lookup. That duplication is the cost of the two services being
-- independently deployable.
CREATE TABLE transaction_status (
    transaction_id UUID PRIMARY KEY,
    account_id     VARCHAR(255) NOT NULL,
    amount_minor   BIGINT NOT NULL,
    currency       CHAR(3) NOT NULL,
    status         VARCHAR(32) NOT NULL,
    accepted_at    TIMESTAMPTZ NOT NULL,
    updated_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Status queries are the reason this table exists, and they filter by account
-- far more often than by transaction id.
CREATE INDEX idx_transaction_status_account ON transaction_status (account_id);
