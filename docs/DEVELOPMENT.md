# Development

## The short version

```bash
docker compose up --build
```

That starts both services, both databases and a broker. Nothing else has to be installed, and no database has to be created by hand.

```bash
curl -i -X POST http://localhost:8080/transactions \
  -H 'Content-Type: application/json' \
  -H 'Idempotency-Key: demo-001' \
  -H 'X-Correlation-Id: demo-trace-1' \
  -d '{"accountId":"acct-001","amountMinor":12500,"currency":"CAD"}'
```

Then follow that one transaction across both services:

```bash
docker compose logs | grep demo-trace-1
```

You should see it accepted by `ledger-api`, published by the relay, and consumed by `ledger-notifier` — three services' worth of log lines tied together by an id you chose. Send the same request twice and the second answers `200` with the same transaction id, and no second event is published.

## What you need

| Tool | Why | Needed for |
| --- | --- | --- |
| Docker | Running the stack, and Testcontainers | `docker compose`, `./mvnw verify` |
| JDK 21 | Building and running outside containers | `./mvnw` |
| PostgreSQL 16 | Only if you run the services outside containers | The manual setup below |

Maven is not in the list: the wrapper (`./mvnw`, `mvnw.cmd`) downloads its own copy on first use.

## Building

```bash
./mvnw test      # unit tests, JDK only, no database, no broker
./mvnw verify    # also runs integration tests, needs a Docker daemon
```

`./mvnw test` is the loop to stay in while writing code. It runs in a couple of minutes and needs nothing installed beyond a JDK.

`./mvnw verify` additionally runs everything named `*IT`, which start real PostgreSQL and Apache Kafka containers through Testcontainers. Those are the tests that prove the guarantees in the README, and they run on every push in CI. **If your machine cannot comfortably run Docker, skip `verify` locally and let CI run it** — the pipeline is configured so nothing is only checked on a developer's laptop.

## The stack

| Service | Port | Notes |
| --- | --- | --- |
| `ledger-api` | 8080 | The HTTP API |
| `ledger-notifier` | 8081 | Consumer; no HTTP API beyond actuator |
| `ledger-db` | 5432 | ledger-api's database |
| `notifier-db` | 5433 | ledger-notifier's database |
| `broker` | 19092 | Redpanda, reachable from the host |

The broker is Redpanda rather than Apache Kafka: same protocol, no JVM, a fraction of the memory. Tests still run against Apache Kafka. [ADR-0005](adr/0005-redpanda-in-the-local-stack.md) explains the split and what it costs.

Useful commands:

```bash
docker compose logs -f ledger-notifier   # follow one service
docker compose down                      # stop, keep nothing
docker compose down -v                   # stop and delete the databases
docker compose ps                        # what is healthy and what is not
```

## Running the services without containers

Only worth doing if you are attaching a debugger. Each service owns its own database — see [ADR-0004](adr/0004-notifier-owns-the-status-projection.md) — and Flyway applies each schema on startup, so only the databases themselves have to exist.

```sql
CREATE USER ledgerflow WITH PASSWORD 'ledgerflow';
CREATE DATABASE ledgerflow OWNER ledgerflow;
CREATE DATABASE ledgerflow_notifier OWNER ledgerflow;
```

Override the credentials with `DB_USERNAME` and `DB_PASSWORD` if you use different ones. A broker still has to be running for events to leave the outbox.

```bash
./mvnw -pl ledger-api spring-boot:run
./mvnw -pl ledger-notifier spring-boot:run
```

If no broker is reachable, `ledger-api` still starts and still accepts transactions: the ledger row and its outbox event are written normally, and the events stay pending until a broker appears. That is the outbox pattern working as designed, and it is worth seeing at least once — stop the broker, post a transaction, look at `outbox_event`, then start the broker again. Expect the Kafka client to fill the log with timeouts while it is away.

## Logging

By default both services log human-readable text, because that is what a developer running them locally wants to read.

The `containers` profile switches the console to [Elastic Common Schema](https://www.elastic.co/guide/en/ecs/current/index.html) — one JSON object per line, with the level, logger, service name and every MDC entry as separate fields. The compose file sets it for both services.

```bash
SPRING_PROFILES_ACTIVE=containers ./mvnw -pl ledger-api spring-boot:run
```

Every log line carries a `correlationId`. It comes from the `X-Correlation-Id` request header when a client sends one, and is generated otherwise; it is stored on the outbox row so the relay can still log under it minutes later, and travels to the consumer as a Kafka header. One search on that field returns everything that happened to a transaction, in both services.

## Notes for Windows

Add the project directory, `%USERPROFILE%\.m2` and the JDK directory to Windows Defender's exclusions. Defender scans every file Maven touches, and excluding them often cuts build times substantially, especially on a mechanical drive.

Run the wrapper as `.\mvnw.cmd`, and quote properties: `.\mvnw.cmd "-Dsome.property=value"`, since PowerShell otherwise splits the argument at the first period.

After extracting files from an archive into the project, run `.\mvnw.cmd clean test` rather than `test`. Extracted files keep the archive's timestamps, which can be older than the compiled classes, and Maven will skip recompiling them.

`docker compose up --build` builds both images from scratch the first time, which downloads a JDK layer and every Maven dependency. On a slow disk expect ten minutes or more for that first run; later runs reuse the cached layers unless a `pom.xml` changes.
