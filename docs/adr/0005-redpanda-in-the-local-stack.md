# ADR-0005: Run Redpanda in the local stack, keep Kafka in tests

- **Status:** Accepted
- **Date:** 2026-08-31

## Context

Until now no broker ran on a developer machine. Integration tests start one through Testcontainers, and those run in CI; the services were started by hand against whatever was available.

A single-command local stack changes that. `docker compose up` has to hold two PostgreSQL instances, a broker and two JVM services at once, and the machine this project is developed on has two cores and a mechanical disk. Apache Kafka's broker is a JVM process that reserves roughly 1 to 2 GB before doing any work — on this hardware that is the difference between a stack that starts and one that swaps.

## Decision

We will run Redpanda in the compose stack, and keep Apache Kafka in the integration tests.

Redpanda implements the Kafka protocol, so no application code changes: the same client library, the same topic, the same headers. The difference is the process — a single native binary with no JVM, which starts in seconds and holds a few hundred megabytes.

Tests keep Apache Kafka because that is what the README claims the project uses, and a test suite that only ever runs against a compatible reimplementation cannot support that claim. CI has the memory to spare; a laptop does not.

## Consequences

The stack starts on modest hardware, which is the difference between a demo someone can run and a demo they read about. Startup drops from tens of seconds to a few.

The cost is that two brokers are now in play, and a Redpanda-only bug would reach the compose stack without any test catching it. That risk is bounded by the protocol surface this project uses — produce, consume, consumer groups, headers — which is the most heavily exercised part of any Kafka-compatible implementation. It would not be bounded if the project used transactions or exactly-once semantics, and adopting either should mean revisiting this decision.

Anyone reading the compose file will also see a broker that the README does not name. That is why this record exists.

## Alternatives considered

**Apache Kafka in compose as well.** The consistent choice, and the right one on a machine with memory to spare. Rejected because on the target machine it makes the stack unusable, and a local stack nobody can run is worth less than a small inconsistency.

**No local stack at all, tests only.** Rejected: the two services agree on the event payload by inspection, and nothing has ever exercised them together. A stack that runs both is what closes that gap.
