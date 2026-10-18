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
- A Docker-compatible container runtime for Quarkus Dev Services (Kafka, PostgreSQL,
  Keycloak, Ollama) once the corresponding backlog items add them. The current
  skeleton runs without it.

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

## Run the packaged application

```shell
./mvnw package -DskipTests
INCIDENT_INSTANCE_ID=incident-assistant-1 java -jar incident-assistant-app/target/quarkus-app/quarkus-run.jar
```
