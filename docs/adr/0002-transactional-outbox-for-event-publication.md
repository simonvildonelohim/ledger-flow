# ADR-0002: Use a transactional outbox for event publication

- **Status:** Accepted
- **Date:** 2026-08-27

## Context

When a transaction is accepted, two things must happen: a row is written to the ledger, and an event is published to Kafka so downstream consumers can react.

These are two separate systems with no shared transaction. Writing to the database and then publishing to the broker leaves a window in which the service can crash: the ledger row exists, the event never goes out, and the downstream state is silently wrong forever. Publishing first and writing after inverts the problem — consumers react to a transaction that was never recorded.

Neither ordering is safe, and no amount of retry logic in the request path closes the window.

## Decision

We will write the ledger row and an `outbox` row in the same database transaction. A separate relay polls unpublished outbox rows and publishes them to Kafka, marking each row published only after the broker acknowledges it.

Consumers must therefore tolerate duplicate delivery, since a crash between publication and acknowledgement will cause a replay. Deduplication is the consumer's responsibility, keyed on the event identifier.

## Consequences

Events are never lost: if the database transaction commits, the event is in the outbox and will eventually be published. The system moves from *exactly once* — which is not achievable across two systems — to *at least once delivery with idempotent effects*, which is.

The costs are real. Publication is asynchronous, so consumers see events after a short delay rather than instantly. The outbox table needs pruning or it grows without bound. And every consumer now carries deduplication logic that would otherwise be unnecessary.

## Alternatives considered

**Two-phase commit across PostgreSQL and Kafka.** Rejected: Kafka's transaction support does not extend to an external database, and distributed transactions carry operational costs disproportionate to this problem.

**Change data capture with Debezium.** A sound alternative that removes the polling relay entirely by reading the write-ahead log. Rejected for now because it introduces Kafka Connect as a third runtime component, and the goal here is to demonstrate the pattern's mechanics explicitly rather than delegate them to a tool. Worth revisiting if throughput ever justifies it.

**Publishing from an `@TransactionalEventListener` after commit.** Rejected: it narrows the failure window without closing it. A crash between commit and publication still loses the event.
