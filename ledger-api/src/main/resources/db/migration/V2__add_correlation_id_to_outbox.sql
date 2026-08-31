-- The correlation id of the request that produced this event.
--
-- Stored rather than inherited from a thread: the relay publishes on a schedule,
-- long after the request that created the row has ended and its thread has been
-- returned to the pool. Without a column, the chain breaks exactly where an
-- investigation needs it most — between accepting a transaction and publishing it.
--
-- Nullable because rows written before this column existed have no id, and
-- inventing one for them would be worse than admitting it is missing.
ALTER TABLE outbox_event ADD COLUMN correlation_id VARCHAR(255);
