# incident-assistant

AI-assisted incident triage service built with Java 25 and Quarkus.

- Alerts are consumed from Kafka.
- A hybrid workflow triages the incident, lets a bounded agent diagnose it using
  RAG over runbooks/postmortems and read-only MCP tools, and proposes a remediation.
- A human approves or rejects the proposal through a REST API.
- Approved actions are executed deterministically through MCP.

See `docs/architecture.md`, `docs/adr/` and `docs/backlog.md`.

## Modules

| Module | Purpose | Dev port |
|---|---|---|
| `incident-assistant-app` | The service: ingestion, workflow, AI steps, approval API | 8080 |
| `ops-tools-mcp` | Mock operations backend exposed as an MCP server | 8081 |

## Prerequisites

- JDK 25 (`JAVA_HOME` pointing to it).
- No local Maven needed: use the Maven Wrapper (`./mvnw`, or `mvnw.cmd` on Windows).
  The build enforces Java 25+ and Maven 3.9.9+.
- A Docker-compatible container runtime for Quarkus Dev Services. Tests and dev mode of
  `incident-assistant-app` start PostgreSQL (`pgvector/pgvector:pg17`) and a Kafka broker
  automatically; Keycloak and Ollama follow with later backlog items.

## Git workflow

Direct commits to `master` are not allowed. Every change is made on a separate branch
(for example `feat/inc-002-incident-persistence`) and merged into `master` afterwards.

The rule is enforced by the versioned pre-commit hook in `.githooks/`. Enable it once per
clone:

```shell
git config core.hooksPath .githooks
```

## Build

```shell
./mvnw spotless:apply   # format Java sources (google-java-format)
./mvnw verify           # compile, run tests, check formatting and build rules
```

`verify` fails on unformatted code, a Java/Maven version below the minimum, or
non-converging dependency versions.

## Run in dev mode

Each module runs in its own terminal:

```shell
./mvnw quarkus:dev -pl incident-assistant-app
./mvnw quarkus:dev -pl ops-tools-mcp -Ddebug=5006
```

The second module needs a different debugger port than the first (default 5005).

Health:

```shell
curl http://localhost:8080/q/health
curl http://localhost:8081/q/health
```

## Configuration

Profiles: `dev` (dev mode), `test` (tests), `prod` (packaged application).
Configuration is bound to `@ConfigMapping` interfaces and validated at startup;
an invalid value stops the application with a message naming the property.

### incident-assistant-app

| Property | Environment variable | Default | Description |
|---|---|---|---|
| `incident.instance.id` | `INCIDENT_INSTANCE_ID` | dev/test: host name, falls back to `local`; prod: `HOSTNAME`, no fallback | Unique id of this instance, used as work-lease owner. Pattern `[A-Za-z0-9][A-Za-z0-9._-]{0,62}`. Reported by the `instance` liveness check. |
| `quarkus.datasource.jdbc.url` | `QUARKUS_DATASOURCE_JDBC_URL` | dev/test: Dev Services; prod: required | PostgreSQL JDBC URL. |
| `quarkus.datasource.username` / `password` | `QUARKUS_DATASOURCE_USERNAME` / `QUARKUS_DATASOURCE_PASSWORD` | dev/test: Dev Services; prod: required | Database credentials. Never put them in `application.properties`. |
| `quarkus.datasource.jdbc.acquisition-timeout` | `QUARKUS_DATASOURCE_JDBC_ACQUISITION_TIMEOUT` | `5S` | Maximum wait for a pooled connection. |
| `quarkus.datasource.jdbc.additional-jdbc-properties.connectTimeout` | | `5` (seconds) | Timeout for opening a connection. |
| `quarkus.datasource.jdbc.additional-jdbc-properties.options` | | `-c statement_timeout=10s` | Server-side limit per SQL statement. |
| `quarkus.datasource.jdbc.additional-jdbc-properties.socketTimeout` | | `30` (seconds) | Client-side limit for a blocked socket read. |
| `quarkus.transaction-manager.default-transaction-timeout` | `QUARKUS_TRANSACTION_MANAGER_DEFAULT_TRANSACTION_TIMEOUT` | `30S` | Maximum duration of a transaction. |
| `kafka.bootstrap.servers` | `KAFKA_BOOTSTRAP_SERVERS` | dev/test: Dev Services; prod: required | Kafka brokers. |
| `incident.alerts.max-payload-bytes` | `INCIDENT_ALERTS_MAX_PAYLOAD_BYTES` | `65536` | Largest accepted alert message; larger ones go to the DLQ unparsed. |
| `smallrye.messaging.worker.<virtual-thread>.max-concurrency` | | `8` | Alerts processed at the same time; keep it below the connection pool size. |
| `quarkus.fault-tolerance."dev.mfa.incident.service.AlertIngestionService/ingest".retry.*` | | 3 retries, 500 ms delay, 30 s total | Retries of an alert after a processing failure, before it goes to the DLQ. |
| `incident.outbox.relay.interval` | `INCIDENT_OUTBOX_RELAY_INTERVAL` | `1s` | Pause between outbox relay runs. |
| `incident.outbox.relay.batch-size` | `INCIDENT_OUTBOX_RELAY_BATCH_SIZE` | `100` | Events per relay transaction (1-1000). |
| `incident.outbox.relay.send-timeout` | `INCIDENT_OUTBOX_RELAY_SEND_TIMEOUT` | `10s` | Longest wait for the broker to acknowledge one event. |
| `incident.outbox.relay.max-batch-duration` | `INCIDENT_OUTBOX_RELAY_MAX_BATCH_DURATION` | `10s` | No new send starts in a batch after this time. |

The schema is managed by Flyway (`incident-assistant-app/src/main/resources/db/migration`)
and migrated at startup; Hibernate only validates that the entities match it. Readiness
(`/q/health/ready`) includes the database and Kafka.

### Kafka topics

| Topic | Direction | Format |
|---|---|---|
| `ops.alerts.v1` | in | `docs/schemas/alert-v1.json` |
| `ops.alerts.v1.dlq` | out | rejected alerts, unchanged, reason in header `dead-letter-reason` |
| `ops.incidents.events.v1` | out | `docs/schemas/incident-event-v1.json`, keyed by incident id |

In dev mode, send an alert to the Dev Services broker (the startup log prints its address
as `-Dkafka.bootstrap.servers=localhost:<port>`) with [kcat](https://github.com/edenhill/kcat):

```shell
echo '{"source":"prometheus","fingerprint":"fp-1","alertName":"HighErrorRate","summary":"Error rate above 5%","startsAt":"2026-10-18T09:58:00Z"}' \
  | kcat -P -b <broker> -t ops.alerts.v1
kcat -C -b <broker> -t ops.incidents.events.v1 -f '%k %s\n' -e
```

## Run the packaged application

```shell
./mvnw package -DskipTests
INCIDENT_INSTANCE_ID=incident-assistant-1 KAFKA_BOOTSTRAP_SERVERS=localhost:9092 java -jar incident-assistant-app/target/quarkus-app/quarkus-run.jar
```
