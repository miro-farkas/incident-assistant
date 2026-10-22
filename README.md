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
| `alert-producer` | Manual testing tool: produces alerts to `ops.alerts.v1` (P-0017) | – |

## Prerequisites

- JDK 25 (`JAVA_HOME` pointing to it).
- No local Maven needed: use the Maven Wrapper (`./mvnw`, or `mvnw.cmd` on Windows).
  The build enforces Java 25+ and Maven 3.9.9+.
- No local Node.js needed: the build installs the pinned version for the web UI (see
  [Web UI](#web-ui)). The first build downloads Node.js, the npm packages and, for the browser
  test, Chromium.
- A Docker-compatible container runtime with Docker Compose v2. Dev mode of
  `incident-assistant-app` runs PostgreSQL (`pgvector/pgvector:pg17`), Kafka and Keycloak
  from `compose-devservices.yml`; tests start their own empty containers through Dev
  Services. Ollama follows with a later backlog item.

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

`.mvn/maven.config` turns Quarkus build analytics off (`-Dquarkus.analytics.disabled=true`), so
builds and dev mode never stop to ask about it.

## Run in dev mode

Each module runs in its own terminal:

```shell
./mvnw quarkus:dev -pl incident-assistant-app
./mvnw quarkus:dev -pl ops-tools-mcp -Ddebug=5006
```

The second module needs a different debugger port than the first (default 5005).

### Local services

Dev mode of `incident-assistant-app` starts the services in `compose-devservices.yml`
(Quarkus Compose Dev Services, P-0016, A-0008) and connects to them automatically. They
keep running, with their data, when dev mode stops; the next dev mode session reuses them
and starts faster.

| Service | Address | Notes |
|---|---|---|
| PostgreSQL | `localhost:5432`, database `incident`, user/password `incident` | local-only credentials |
| Kafka | `localhost:9092` (containers on the compose network: `kafka:19092`) | single node, KRaft; topic auto-creation off |
| Keycloak | `http://localhost:8180`, realm `incident-assistant`; admin console user/password `admin` | data in schema `keycloak` of PostgreSQL |

`kafka-init` creates the topics once the broker is healthy and exits; `postgres-init`
creates the `keycloak` schema before Keycloak starts. Keycloak imports
`keycloak/realm-incident-assistant.json` on its first start only, so changes made in the
admin console survive restarts; to re-import the file, drop the schema or start clean.
Manage the services with the Docker Compose CLI:

```shell
docker compose -f compose-devservices.yml ps        # state
docker compose -f compose-devservices.yml down      # stop, keep data
docker compose -f compose-devservices.yml down -v   # stop and delete data (clean start)
```

A clean start is needed after changing a migration that was already applied locally.

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
| `quarkus.datasource.jdbc.url` | `QUARKUS_DATASOURCE_JDBC_URL` | dev: `compose-devservices.yml`; test: Dev Services; prod: required | PostgreSQL JDBC URL. |
| `quarkus.datasource.username` / `password` | `QUARKUS_DATASOURCE_USERNAME` / `QUARKUS_DATASOURCE_PASSWORD` | dev: `compose-devservices.yml`; test: Dev Services; prod: required | Database credentials. Never put them in `application.properties`. |
| `quarkus.datasource.jdbc.acquisition-timeout` | `QUARKUS_DATASOURCE_JDBC_ACQUISITION_TIMEOUT` | `5S` | Maximum wait for a pooled connection. |
| `quarkus.datasource.jdbc.additional-jdbc-properties.connectTimeout` | | `5` (seconds) | Timeout for opening a connection. |
| `quarkus.datasource.jdbc.additional-jdbc-properties.options` | | `-c statement_timeout=10s` | Server-side limit per SQL statement. |
| `quarkus.datasource.jdbc.additional-jdbc-properties.socketTimeout` | | `30` (seconds) | Client-side limit for a blocked socket read. |
| `quarkus.transaction-manager.default-transaction-timeout` | `QUARKUS_TRANSACTION_MANAGER_DEFAULT_TRANSACTION_TIMEOUT` | `30S` | Maximum duration of a transaction. |
| `kafka.bootstrap.servers` | `KAFKA_BOOTSTRAP_SERVERS` | dev: `compose-devservices.yml`; test: Dev Services; prod: required | Kafka brokers. |
| `quarkus.oidc.auth-server-url` | `OIDC_AUTH_SERVER_URL` | dev: compose Keycloak, realm `incident-assistant`; test: off (`@TestSecurity`); prod: required | Issuer of the bearer tokens, for example `https://sso.example.com/realms/incident-assistant`. Roles from `realm_access.roles`; the approver identity is the token `sub`. |
| `quarkus.oidc.client-id` | `QUARKUS_OIDC_CLIENT_ID` | `incident-assistant` | Client of the service in the identity provider. |
| `incident.alerts.max-payload-bytes` | `INCIDENT_ALERTS_MAX_PAYLOAD_BYTES` | `65536` | Largest accepted alert message; larger ones go to the DLQ unparsed. |
| `smallrye.messaging.worker.<virtual-thread>.max-concurrency` | | `8` | Alerts processed at the same time; keep it below the connection pool size. |
| `quarkus.fault-tolerance."dev.mfa.incident.service.AlertIngestionService/ingest".retry.*` | | 3 retries, 500 ms delay, 30 s total | Retries of an alert after a processing failure, before it goes to the DLQ. |
| `incident.outbox.relay.interval` | `INCIDENT_OUTBOX_RELAY_INTERVAL` | `1s` | Pause between outbox relay runs. |
| `incident.outbox.relay.batch-size` | `INCIDENT_OUTBOX_RELAY_BATCH_SIZE` | `100` | Events per relay transaction (1-1000). |
| `incident.outbox.relay.send-timeout` | `INCIDENT_OUTBOX_RELAY_SEND_TIMEOUT` | `10s` | Longest wait for the broker to acknowledge one event. |
| `incident.outbox.relay.max-batch-duration` | `INCIDENT_OUTBOX_RELAY_MAX_BATCH_DURATION` | `10s` | No new send starts in a batch after this time. |
| `incident.stream.heartbeat-interval` | `INCIDENT_STREAM_HEARTBEAT_INTERVAL` | `15s` | Interval of the heartbeat comment on idle event streams. |
| `incident.stream.client-buffer-size` | `INCIDENT_STREAM_CLIENT_BUFFER_SIZE` | `256` | Events buffered per stream (1-10000); a client further behind is disconnected. |
| `incident.stream.max-streams` | `INCIDENT_STREAM_MAX_STREAMS` | `200` | Concurrent event streams per instance; above it `503`. |
| `incident.stream.retry-after` | `INCIDENT_STREAM_RETRY_AFTER` | `5s` | `Retry-After` sent with that `503` (at least 1 s). |

The schema is managed by Flyway (`incident-assistant-app/src/main/resources/db/migration`)
and migrated at startup; Hibernate only validates that the entities match it. Readiness
(`/q/health/ready`) includes the database and Kafka.

### Approval API

REST API under `/api/v1/incidents` (A-0006), bearer tokens from Keycloak (the web UI uses a
session cookie instead, see [Web UI](#web-ui)), errors as RFC 9457 Problem Details. OpenAPI: `/q/openapi`, Swagger UI in dev mode: `/q/swagger-ui`.

| Endpoint | Role | |
|---|---|---|
| `GET /api/v1/incidents?state=…&page=0&size=20` | `incident-viewer` or `incident-approver` | newest first, `state` repeatable; each item with its `occurrenceCount` |
| `GET /api/v1/incidents/{id}` | `incident-viewer` or `incident-approver` | alert, steps, decision; `ETag` = version |
| `POST /api/v1/incidents/{id}/decision` | `incident-approver` | `{"decision": "APPROVE"\|"REJECT", "comment": …}` with `If-Match` |
| `GET /api/v1/incidents/stream` | `incident-viewer` or `incident-approver` | Server-Sent Events: every incident event from now on (A-0009) |
| `GET /api/v1/me` | any authenticated user | display name and `incident-*` roles |

The local realm has two users (passwords as user names, local only): `viewer`
(`incident-viewer`) and `approver` (both roles). The client `incident-assistant` allows the
password grant for local manual testing:

```shell
TOKEN=$(curl -s -X POST http://localhost:8180/realms/incident-assistant/protocol/openid-connect/token \
  -d grant_type=password -d client_id=incident-assistant \
  -d client_secret=incident-assistant-local-secret -d username=approver -d password=approver \
  | jq -r .access_token)
curl -i -H "Authorization: Bearer $TOKEN" http://localhost:8080/api/v1/incidents/<id>
curl -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' -H 'If-Match: "<version>"' \
  -d '{"decision": "APPROVE", "comment": "Rollback is safe"}' \
  http://localhost:8080/api/v1/incidents/<id>/decision
```

Until the AI steps exist (INC-006 to INC-008) no incident reaches `AWAITING_APPROVAL` in a
real run, so a decision answers `409`.

The event stream forwards each `incident.state-changed` and `incident.occurrence-recorded`
event unchanged (`event:` is the event type, `id:` the event id, `data:` the JSON) and sends `: heartbeat` comments while idle.
Events are hints: a client reloads what it shows when it connects or reconnects; nothing is
replayed. Watch it while sending alerts (for example with `alert-producer`):

```shell
curl -N -H "Authorization: Bearer $TOKEN" http://localhost:8080/api/v1/incidents/stream
```

### Web UI

The incident UI (A-0010, P-0019) is a React + TypeScript app in
`incident-assistant-app/src/main/webui`, built with Vite and served by the service through
Quarkus Quinoa on the same origin as the API.

- Open `http://localhost:8080` while `./mvnw quarkus:dev -pl incident-assistant-app` runs and sign
  in as `approver`/`approver` (sees the decision buttons) or `viewer`/`viewer` (reads only). Dev
  mode starts the Vite dev server (port 5173) behind the service, so UI changes reload live.
- Screens: incident list (filter by state, open states by default, paging, `AWAITING_APPROVAL`
  highlighted, occurrences count per incident), detail (alert, steps with their output as key/value text, occurrences count,
  decision) and the decision itself (approve or reject with an optional comment). Both screens
  update live from the event stream; the header shows the stream state.
- Login: the UI calls `GET /api/v1/me`; without a session it sends the browser to `/login`, where
  the service runs the OIDC code flow with Keycloak and sets an encrypted `HttpOnly`, `Secure`,
  `SameSite=Strict` session cookie (`q_session`); no token reaches JavaScript. "Sign out" (`/logout`)
  ends the session of the service and the Keycloak session. Bearer tokens keep working on all API
  endpoints.
- CSRF: a state-changing request authenticated by the session cookie must carry
  `X-Requested-By: incident-ui` (the UI sets it); without it the service answers `403`.
- Every response except `/q/*` carries a strict `Content-Security-Policy` (scripts, styles and
  connections only from the service itself, no inline code, no framing); dev mode relaxes it for
  the Vite dev server.

How the UI is built:

- Quinoa installs the pinned Node.js (`quarkus.quinoa.package-manager-install.node-version`) into
  `incident-assistant-app/.quinoa` and runs `npm ci` with the committed `package-lock.json`; no local
  Node.js is needed. `./mvnw package`/`verify` also runs the UI tests (`npm test`: type check and
  Vitest with Testing Library).
- API types are generated with `openapi-typescript` from the snapshot
  `src/main/webui/openapi/incident-assistant.json`. `OpenApiTest` compares the snapshot with the
  service's OpenAPI document; when the API changed it rewrites the snapshot and fails, so review
  the diff and build again. A change that breaks the UI then fails its type check.
- `IncidentUiEndToEndTest` (test package `dev.mfa.incident.webui`) drives the UI with Playwright
  (Chromium, downloaded on first use) against a Keycloak Dev Service: login, approve, the list in
  a second tab changes to `APPROVED` live; session cookie, CSRF header, CSP and logout.
- To work on the UI alone: `cd incident-assistant-app/src/main/webui`, then `npm ci` and `npm test`
  (needs Node.js 24 on the path, or the one Quinoa installed in `incident-assistant-app/.quinoa/node`).

| Property | Environment variable | Default | Description |
|---|---|---|---|
| `quarkus.oidc.credentials.secret` | `OIDC_CLIENT_SECRET` | dev: local realm secret; prod: required | Secret of the confidential client `incident-assistant`, used to redeem the login code. Also derives the keys of the state and session cookies; use at least 32 characters, otherwise the state key is generated per instance and a login started on one instance fails on another. |

### Kafka topics

| Topic | Direction | Format |
|---|---|---|
| `ops.alerts.v1` | in | `docs/schemas/alert-v1.json` |
| `ops.alerts.v1.dlq` | out | rejected alerts, unchanged, reason in header `dead-letter-reason` |
| `ops.incidents.events.v1` | out; in for the event stream (group `incident-assistant-stream-<instance id>`, no commits) | `incident.state-changed`: `docs/schemas/incident-event-v1.json`; `incident.occurrence-recorded` (repeated alert of an open incident, A-0012): `docs/schemas/incident-occurrence-event-v1.json`; both keyed by incident id |

In dev mode, send an alert to the local broker with [kcat](https://github.com/edenhill/kcat):

```shell
echo '{"source":"prometheus","fingerprint":"fp-1","alertName":"HighErrorRate","summary":"Error rate above 5%","startsAt":"2026-10-18T09:58:00Z"}' \
  | kcat -P -b localhost:9092 -t ops.alerts.v1
kcat -C -b localhost:9092 -t ops.incidents.events.v1 -f '%k %s\n' -e
```

Without a local kcat, run it in a container on the compose network:

```shell
echo '<alert JSON>' | docker run --rm -i --network incident-assistant_default edenhill/kcat:1.7.1 \
  -b kafka:19092 -P -t ops.alerts.v1
```

### ops-tools-mcp

MCP server with deterministic fake data for the services `checkout`, `payments` and
`inventory` (A-0005). Read and action tools are served on separate endpoints
(Streamable HTTP; legacy SSE at `<path>/sse`):

| Endpoint | Tools | Access |
|---|---|---|
| `/mcp/read` | `get_service_metrics`, `search_logs`, `get_recent_deployments`, `get_service_health` | open |
| `/mcp/actions` | `restart_service`, `rollback_deployment`, `scale_service` | header `X-Api-Key` |

| Property | Environment variable | Default | Description |
|---|---|---|---|
| `ops.actions.api-key` | `OPS_ACTIONS_API_KEY` | dev/test: fixed non-secret value; prod: required | Key that clients of `/mcp/actions` send in `X-Api-Key`. At least 32 characters. |
| `ops.services.<service>.scenario` | `OPS_SERVICES_<SERVICE>_SCENARIO` | checkout `bad-deployment`, payments `downstream-timeout`, inventory `memory-leak` | Simulated situation: `healthy`, `bad-deployment`, `memory-leak`, `downstream-timeout`. Only catalogued services are accepted. |
| `ops.services.<service>.fault` | `OPS_SERVICES_<SERVICE>_FAULT` | `none` | Simulated backend failure: `none`, `unavailable` (tool error), `slow`. |
| `ops.faults.slow-delay` | `OPS_FAULTS_SLOW_DELAY` | `5s` | Delay of every call for a service with fault `slow` (up to 5 minutes). |
| `ops.fake-data.anchor` | `OPS_FAKE_DATA_ANCHOR` | test: `2026-10-18T10:00:00Z`; otherwise unset | Fixed "now" of the simulated timeline; unset means the current minute. |
| `ops.actions.idempotency-retention` | `OPS_ACTIONS_IDEMPOTENCY_RETENTION` | `24h` | How long an action result is replayed for its idempotency key. |
| `ops.actions.max-idempotency-records` | `OPS_ACTIONS_MAX_IDEMPOTENCY_RECORDS` | `10000` | Idempotency records kept in memory; when full, new actions are refused until records expire. |

Try it in dev mode with the [MCP Inspector](https://github.com/modelcontextprotocol/inspector)
(`npx @modelcontextprotocol/inspector`, transport "Streamable HTTP"):
`http://localhost:8081/mcp/read`, or `http://localhost:8081/mcp/actions` with header
`X-Api-Key: dev-only-actions-key-not-a-secret-0001`.

### Alert producer

For testing load or duplicate handling by hand, `alert-producer` sends generated alerts to
the local broker at a fixed rate until stopped with Ctrl+C. Rate (`ALERTS_PER_MINUTE`),
total count and the size of the fingerprint pool are constants at the top of
`AlertProducer.java`.

```shell
./mvnw -q -pl alert-producer compile exec:java
```

## Run the packaged application

Against the local services:

```shell
./mvnw package -DskipTests
docker compose -f compose-devservices.yml up -d --wait
INCIDENT_INSTANCE_ID=incident-assistant-1 \
  QUARKUS_DATASOURCE_JDBC_URL=jdbc:postgresql://localhost:5432/incident \
  QUARKUS_DATASOURCE_USERNAME=incident QUARKUS_DATASOURCE_PASSWORD=incident \
  KAFKA_BOOTSTRAP_SERVERS=localhost:9092 \
  java -jar incident-assistant-app/target/quarkus-app/quarkus-run.jar
OPS_ACTIONS_API_KEY=<at least 32 characters> QUARKUS_HTTP_PORT=8081 \
  java -jar ops-tools-mcp/target/quarkus-app/quarkus-run.jar
```
