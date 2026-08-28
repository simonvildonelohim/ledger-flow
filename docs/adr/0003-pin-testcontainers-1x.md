# ADR-0003: Pin Testcontainers to the 1.x line, not 2.0

- **Status:** Accepted
- **Date:** 2026-08-27

## Context

Testcontainers 2.0 renames every module artifact (`org.testcontainers:postgresql` becomes `org.testcontainers:testcontainers-postgresql`, and so on for every database and service module) and relocates container classes into new packages. It also drops JUnit 4 support.

This project's only use of Testcontainers so far is a single `@ServiceConnection`-backed PostgreSQL container in `LedgerApiApplicationIT`. That is a small surface, but adopting 2.0 means committing to the new coordinates now, before the project has enough integration tests to be confident the migration was done correctly everywhere it matters.

## Decision

We will pin `testcontainers-bom` to `1.21.3` — the last release before the 2.0 rename — via explicit dependency management in the root `pom.xml`, rather than leaving the version to whatever Spring Boot's own dependency management resolves.

## Consequences

The project builds against a well-documented, widely-used API surface. The trade-off is a deliberate, tracked one version behind: Dependabot will open a pull request proposing testcontainers-bom 2.x, and that PR should be reviewed as a real migration — updating artifact coordinates and imports — rather than merged as a routine version bump.

## Alternatives considered

**Let Spring Boot's dependency management choose the Testcontainers version.** Rejected for now: it would make the project's behaviour depend on which Testcontainers major Spring Boot 4.1 happens to manage, which is not something this ADR's author has verified, and an unverified assumption has no place in a decision about what ships.

**Adopt Testcontainers 2.0 immediately.** Rejected for now: the rename would need to be verified against real, current Maven Central coordinates and package names rather than assumed, and that verification is better done as its own reviewed change than folded into the project's very first integration test.
