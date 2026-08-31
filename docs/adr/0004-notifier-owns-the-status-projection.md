# ADR-0004: ledger-notifier owns the status projection

- **Status:** Accepted
- **Date:** 2026-08-31

## Context

Consuming a transaction event has to result in a status somewhere. The ledger row that records the transaction lives in ledger-api's database; the service that learns the transaction has been processed is ledger-notifier, which owns a different database.

Three placements were available, and the choice determines how coupled the two services stay.

## Decision

We will have ledger-notifier maintain its own `transaction_status` table, in its own database, and treat that table as the answer to "what is the status of this transaction?".

The projection is written by a `TransactionEventHandler`, which means it runs inside the same transaction as the deduplication claim. The event id and the status change therefore commit together or not at all, with no extra machinery.

The write is an upsert, so it is idempotent on its own rather than relying on deduplication to protect it. Deduplication guards against the same event arriving twice; it says nothing about two different events that would produce the same state, and a projection that is only correct because something upstream is correct is a projection that breaks the first time that assumption stops holding.

## Consequences

Each service writes only to its own database, so a migration in one cannot break the other and either can be restored independently. Read load for status queries falls on ledger-notifier rather than on the write path.

The costs are real and worth stating plainly. The `status` column on ledger-api's `transactions` table now stays `PENDING` forever — it records what was true at intake and nothing updates it afterward, which will look like a bug to anyone reading that table without knowing this record exists. Status is also eventually consistent: a client that submits a transaction and immediately asks for its status may still see `PENDING`, and that window is unbounded when the broker is unavailable.

The projection duplicates fields that already exist in ledger-api's ledger. That duplication is the price of independence, not an oversight.

## Alternatives considered

**ledger-notifier writes to ledger-api's database.** Rejected: two services writing one table means neither owns it. A schema change would require coordinating a deployment of both, which removes most of the reason for splitting them.

**ledger-notifier calls an endpoint on ledger-api.** A defensible design, and the one to revisit if the duplicated fields become a maintenance problem. Rejected for now because it puts a synchronous call in the middle of an asynchronous pipeline: ledger-api being unavailable would stop consumption, and the outbox exists precisely so that one service being down does not stop another.

## Known simplification

Consuming `TransactionAccepted` sets the status to `SETTLED`. In a real ledger, settlement is a separate event emitted after a downstream system confirms the movement of money, and acceptance would leave the transaction pending until then. Nothing in this project produces that confirmation yet, so consumption stands in for it. This is a modelling shortcut, not a claim about how settlement works.
