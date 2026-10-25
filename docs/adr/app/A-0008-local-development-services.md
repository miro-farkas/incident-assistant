# A-0008: Local development services

- Status: Accepted
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
  It carries the labels `io.quarkus.devservices.compose.ignore` (while it runs, Quarkus
  would otherwise take it for the broker, as it uses the same image) and
  `io.quarkus.devservices.compose.wait_for.ports.disable` (Quarkus does not wait for it).
- Compose Dev Services is disabled in the `%test` profile; tests keep
  `quarkus.datasource.devservices.*` and `quarkus.kafka.devservices.*`.
- In the `%dev` profile, Kafka Dev Services does not look for a shared broker
  (`quarkus.kafka.devservices.shared=false`); otherwise a broker started by another dev
  mode application takes precedence over the compose service.
- Keycloak and Ollama are added to the same file by the backlog items that introduce them;
  Ollama models go into a named volume.
- Keycloak (added by INC-009):
  - service `keycloak`, image version pinned to the one the platform's Keycloak Dev
    Services uses for tests, `start-dev` on host port `8180`;
  - its data in the schema `keycloak` of the compose PostgreSQL (`KC_DB=postgres`,
    `KC_DB_SCHEMA=keycloak`), so it lives on the kept `incident-assistant-pgdata` volume;
    no separate database or volume;
  - a run-once service `postgres-init` creates the schema with `CREATE SCHEMA IF NOT
    EXISTS` before Keycloak starts (labels as `kafka-init`, so Quarkus ignores it), which
    also works for volumes created before the schema existed;
  - the realm (clients, roles, one user per role) is imported from a checked-in file with
    `--import-realm`, which skips a realm that already exists, so local changes survive
    restarts.
- One database for all modules: each further module gets its own schema in the compose
  PostgreSQL (for example `notification`, A-0011), never a second database.
- Mailpit (added by INC-016, A-0011): service `mailpit`, image `axllent/mailpit` (pinned),
  SMTP on host port `1025`, web UI on `8025`, no volume (mails live in memory). Dev mode of
  `notification-service` sends to it; tests use the mailer's mock mailbox.
- Ollama (added by INC-005, A-0013): service `ollama`, image `ollama/ollama` (pinned), host
  port `11434`, models in volume `incident-assistant-ollama`. Dev mode of
  `incident-assistant-app` finds it at the default address and pulls the embedding model on
  first use; tests use a fake embedding model.

## Alternatives considered
- `apache/kafka-native` (the current Dev Services image): faster start, but no CLI for
  topic creation or debugging.
- Broker auto-creation of topics instead of `kafka-init`: one partition count for all
  topics, and typos in topic names create topics silently.

## Consequences
- The README's local Kafka instructions use the fixed address `localhost:9092`.
- Topic and image changes have to be made in both `application.properties` (tests) and
  `compose-devservices.yml` (dev).
- Verified with Quarkus 3.40.1: a fresh dev mode start creates the project and topics, a
  later start discovers the running services with their data, and with
  `%test.quarkus.compose.devservices.enabled=false` tests use the regular Dev Services.
