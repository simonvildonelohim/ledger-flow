# Changelog

All notable changes to this project are documented here.

The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

## [0.2.0] - 2026-08-31

Closes the loop. A transaction submitted over HTTP now travels the whole path —
ledger, outbox, broker, consumer, status — and every guarantee the README claims
is backed by a test running against real containers in CI.

### Added

- `ledger-notifier` consumes `transactions.v1`, having been an empty module since
  the first milestone.
- Deduplication on the event id, recorded in a `processed_event` table. The claim
  and the effect share one transaction, so an event is never marked handled
  without its effect having happened.
- Transaction status projected into ledger-notifier's own database, queryable
  independently of ledger-api.
- The outbox event id now travels as a Kafka header, which is what gives the
  consumer something to deduplicate on.
- `NotifierDeduplicationIT`, proving the same event delivered twice is handled
  once, and that a second distinct event about the same transaction still is.
- ADR-0004, recording where status lives and what that choice costs.

### Changed

- `ledger-notifier` now owns a database. It no longer shares ledger-api's.

### Known gaps

- The `status` column in ledger-api's `transactions` table stays `PENDING`
  forever. Status is owned by ledger-notifier's projection — see ADR-0004.
- Nothing exercises both services in one run, so they agree on the event payload
  by inspection rather than by test.
- `processed_event` grows without bound, like the outbox before it.
- A message that cannot be parsed, or that carries no event id, is logged and
  dropped. A dead-letter topic is the production answer.

## [0.1.0] - 2026-08-29

First milestone with the transactional outbox working end to end: a transaction
accepted over HTTP is recorded and its event reaches Kafka, with no window in
which one exists without the other.

### Added

- `POST /transactions` accepting a transaction, with an `Idempotency-Key` header
  that makes retries safe. A repeated key returns the original transaction with
  `200` instead of creating a second one.
- Request validation covering blank account ids, zero amounts, and currency codes
  that are not ISO-4217. Amounts are stored in minor units as integers.
- Error responses following RFC 9457, naming each rejected field.
- Transactional outbox: the ledger row and its event are written in one database
  transaction, so an accepted transaction always has an event waiting for it.
- Outbox relay publishing to Kafka, claiming rows with `FOR UPDATE SKIP LOCKED`
  and marking them published only after the broker acknowledges the send.
- `transactions.v1` topic with three partitions, keyed by account id so events for
  one account keep their order.
- Integration tests running against real PostgreSQL and Kafka containers in CI,
  including concurrent submission of one idempotency key, a write that fails
  mid-transaction, and a broker that is unavailable.
- Architecture decision records, contribution guide, and development setup notes.

### Fixed

- Migrations never ran: with only `flyway-core` on the classpath, Spring Boot 4's
  modular auto-configuration left Flyway present as a library but never invoked.
- The context-load test passed without applying any migration, since it never
  queried a table. It now asserts the migrated tables exist.
- Error responses fell back to Spring's default body. The advice was catching
  `MethodArgumentNotValidException`, but a controller method carrying a constraint
  on a parameter raises `HandlerMethodValidationException` instead — and Spring
  Boot's own problem-details advice, registered at order 0, answered first.
- `mvnw` was committed without its executable bit, so the Linux CI runner refused
  to run it, and `.gitignore` excluded `maven-wrapper.properties`, which the
  wrapper needs.

[Unreleased]: https://github.com/simonvildonelohim/ledger-flow/compare/v0.2.0...HEAD
[0.2.0]: https://github.com/simonvildonelohim/ledger-flow/compare/v0.1.0...v0.2.0
[0.1.0]: https://github.com/simonvildonelohim/ledger-flow/releases/tag/v0.1.0
