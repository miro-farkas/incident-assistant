# A-0008: Local development services

- Status: Proposed
- Date: 2026-10-19
- Applies: P-0016, P-0010

## Context
incident-assistant needs PostgreSQL with pgvector (A-0002, A-0004) and a Kafka broker with
the topics of A-0003 and A-0007. Keycloak (A-0006) and Ollama (P-0008) follow with later
backlog items. Tests start these through Dev Services with the configuration in
`application.properties`.

## Decision
- `compose-devservices.yml` sets the compose project name `incident-assistant` (top-level
  `name`), the same value as `quarkus.compose.devservices.project-name`, so dev mode and the
  `docker compose` CLI address the same running services. It defines:

  | Service | Image | Host port | Volume |
  |---|---|---|---|
  | `postgres` | `pgvector/pgvector:pg17` | `5432` | `incident-assistant-pgdata` |
  | `kafka` | `apache/kafka:4.2.0`, single node, KRaft combined broker and controller | `9092` | `incident-assistant-kafka` |
  | `kafka-init` | same image as `kafka`, runs once | – | – |

- `kafka` advertises `PLAINTEXT://localhost:9092` for clients on the host and a separate
  listener for containers on the compose network. The JVM image is used rather than
  `apache/kafka-native` because it ships the Kafka CLI, which `kafka-init` and local
  debugging need.
- `kafka-init` creates `ops.alerts.v1` (1 partition), `ops.alerts.v1.dlq` (1) and
  `ops.incidents.events.v1` (3) with `--if-not-exists`, matching the test topics. Broker
  auto-creation stays off, so a wrong topic name fails instead of creating a topic.
- Compose Dev Services is disabled in the `%test` profile; tests keep
  `quarkus.datasource.devservices.*` and `quarkus.kafka.devservices.*`.
- Keycloak and Ollama are added to the same file by the backlog items that introduce them;
  Ollama models go into a named volume.

## Alternatives considered
- `apache/kafka-native` (the current Dev Services image): faster start, but no CLI for
  topic creation or debugging.
- Broker auto-creation of topics instead of `kafka-init`: one partition count for all
  topics, and typos in topic names create topics silently.

## Consequences
- The README's local Kafka instructions use the fixed address `localhost:9092`.
- Topic and image changes have to be made in both `application.properties` (tests) and
  `compose-devservices.yml` (dev).
- Implementation must verify with Quarkus 3.40 that `kafka-init` exits without failing the
  dev mode start, and that `%test.quarkus.compose.devservices.enabled=false` makes tests
  fall back to the regular Dev Services.
