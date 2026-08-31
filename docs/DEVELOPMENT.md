# Development

## What you need

| Tool | Why | Needed for |
| --- | --- | --- |
| JDK 21 | Building and running | Everything |
| PostgreSQL 16 | The ledger and the projection | Running the services |
| Docker | Testcontainers | `./mvnw verify` only |
| Kafka | Event publication | Running the services end to end |

Maven is not in the list: the wrapper (`./mvnw`, `mvnw.cmd`) downloads its own copy on first use.

## Building

```bash
./mvnw test      # unit tests, JDK only, no database, no broker
./mvnw verify    # also runs integration tests, needs a Docker daemon
```

`./mvnw test` is the loop to stay in while writing code. It runs in a couple of minutes and needs nothing installed beyond a JDK.

`./mvnw verify` additionally runs everything named `*IT`, which start real PostgreSQL and Kafka containers through Testcontainers. Those are the tests that prove the guarantees in the README, and they run on every push in CI. **If your machine cannot comfortably run Docker, skip `verify` locally and let CI run it** — the pipeline is configured so nothing is only checked on a developer's laptop.

## Running the services locally

### Databases

Each service owns its own database. See [ADR-0004](adr/0004-notifier-owns-the-status-projection.md) for why. Flyway applies each schema on startup, so only the databases themselves have to exist.

```sql
CREATE USER ledgerflow WITH PASSWORD 'ledgerflow';
CREATE DATABASE ledgerflow OWNER ledgerflow;
CREATE DATABASE ledgerflow_notifier OWNER ledgerflow;
```

Override the credentials with `DB_USERNAME` and `DB_PASSWORD` if you use different ones.

### Kafka

Kafka runs in KRaft mode — no ZooKeeper. The quickest way is a single container:

```bash
docker run -d --name kafka -p 9092:9092 apache/kafka:3.8.0
```

`ledger-api` creates the `transactions.v1` topic itself on startup, so there is no manual topic setup.

If you are not running Kafka, `ledger-api` still starts and still accepts transactions: the ledger row and its outbox event are written normally, and the events stay pending until a broker is available. That is the outbox pattern working as designed, and it is worth seeing at least once — stop the broker, post a transaction, look at `outbox_event`, then start the broker again.

### Starting them

Two terminals:

```bash
./mvnw -pl ledger-api spring-boot:run
./mvnw -pl ledger-notifier spring-boot:run
```

Then post a transaction:

```bash
curl -i -X POST http://localhost:8080/transactions \
  -H 'Content-Type: application/json' \
  -H 'Idempotency-Key: demo-001' \
  -d '{"accountId":"acct-001","amountMinor":12500,"currency":"CAD"}'
```

Send it twice. The first call answers `201`, the second `200` with the same transaction id — and only one event is published, which you can see in the notifier's output.

Within a second or so, `ledger-notifier` logs the consumed event and writes a row to its `transaction_status` table.

## Logging

By default both services log human-readable text, because that is what a developer running them locally wants to read.

The `containers` profile switches the console to [Elastic Common Schema](https://www.elastic.co/guide/en/ecs/current/index.html) — one JSON object per line, with the level, logger, service name and every MDC entry as separate fields. Anything running in a container should use it:

```bash
SPRING_PROFILES_ACTIVE=containers ./mvnw -pl ledger-api spring-boot:run
```

The reason for the format is queryability. Once two services interleave their output, grepping unstructured text stops working; a JSON field can be filtered on exactly. This is the groundwork for the correlation id, which will let one query follow a single transaction across both services.

## Notes for Windows

Add the project directory, `%USERPROFILE%\.m2` and the JDK directory to Windows Defender's exclusions. Defender scans every file Maven touches, and excluding them often cuts build times substantially, especially on a mechanical drive.

Run the wrapper as `.\mvnw.cmd`, and quote properties: `.\mvnw.cmd "-Dsome.property=value"`, since PowerShell otherwise splits the argument at the first period.

After extracting files from an archive into the project, run `.\mvnw.cmd clean test` rather than `test`. Extracted files keep the archive's timestamps, which can be older than the compiled classes, and Maven will skip recompiling them.
