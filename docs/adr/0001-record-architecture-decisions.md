# ADR-0001: Record architecture decisions

- **Status:** Accepted
- **Date:** 2026-08-27

## Context

This project involves several decisions that are hard to reverse once code depends on them: how events are published, how idempotency is enforced, how failures are retried. Decisions made silently are re-litigated later, usually badly, because the constraints that justified them have been forgotten.

## Decision

We will record architecturally significant decisions as Architecture Decision Records, stored in `docs/adr/`, numbered sequentially, and written before the corresponding implementation.

A decision is architecturally significant when it is expensive to reverse, when it affects more than one module, or when a reviewer would reasonably ask "why this way?".

Accepted records are immutable. A decision that no longer holds is superseded by a new record that links back to it.

## Consequences

Reviewers can reconstruct the reasoning behind the design without archaeology. The cost is roughly twenty minutes per significant decision, and the discipline to write the record before the code rather than after.

## Alternatives considered

**Documenting decisions in the wiki.** Rejected: the wiki is not versioned alongside the code, so it drifts out of sync with what was actually built.

**Documenting decisions in commit messages only.** Rejected: commit messages explain a change, not a standing constraint, and they are not discoverable by someone reading the repository for the first time.
