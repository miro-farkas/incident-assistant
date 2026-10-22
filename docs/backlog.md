# Backlog

Implement strictly in order — the order of this file, not of the numbers (ids are stable
once assigned). Each item is a vertical, shippable increment.
Status: `TODO` → `IN PROGRESS` → `DONE`. Update the status as part of the item.

---

## INC-001 Project skeleton — DONE
Maven multi-module build (`incident-assistant-app`, `ops-tools-mcp`), Java 25, latest
stable Quarkus platform supporting Java 25 (pinned via BOM; verify the version online).

Acceptance criteria
- Maven Wrapper, parent POM with dependency management, `maven-enforcer` (Java 25,
  Maven version, dependency convergence), Spotless with google-java-format.
- `.gitignore`, `.editorconfig`.
- Both modules start in dev mode; `/q/health` returns UP.
- Config profiles `dev`, `test`, `prod`; `@ConfigMapping` example validated at startup.
- ArchUnit test module with the first rule (package root).
- README: prerequisites, build, run.

## INC-002 Incident domain and persistence — DONE
Covers A-0001, A-0002, P-0003, P-0013, P-0014.

Acceptance criteria
- Flyway migrations for `incident`, `incident_step`, `approval_decision`, `outbox_event`.
- `IncidentState` enum and a single `IncidentStateMachine` defining allowed transitions;
  illegal transition throws a domain exception. Fully unit-tested (every allowed and a
  representative set of illegal transitions).
- Optimistic locking on `incident`.
- Repository/service API to create an incident and record a step result + transition in
  one transaction, writing an outbox event.

## INC-003 Kafka alert ingestion — DONE
Covers A-0003, P-0004.

Acceptance criteria
- `docs/schemas/alert-v1.json` and `AlertMessage` record with validation.
- Consumer on `ops.alerts.v1` on a virtual thread creates incidents in `RECEIVED`.
- Duplicate alert for an open incident records an occurrence, no new incident.
- Invalid payload → DLQ `ops.alerts.v1.dlq` with error header.
- Outbox relay publishes state events to `ops.incidents.events.v1` keyed by incident id.
- Integration tests with Kafka Dev Services for: happy path, duplicate, invalid payload.

## INC-004 Ops tools MCP server (mock) — DONE
Covers A-0005, P-0006.

Acceptance criteria
- `ops-tools-mcp` exposes read tools and action tools as specified in A-0005, following
  skill `mcp-tool-conventions`.
- Deterministic fake data for at least three services (`checkout`, `payments`,
  `inventory`) with scenarios: bad deployment, memory leak, downstream timeout.
- Scenario selectable per service via config to make tests deterministic.
- Action tools require an idempotency key; repeated call returns the original result.
- Separation of read vs. action access implemented and documented in A-0005
  (update ADR with the actual mechanism).
- Tests for each tool, including error and size-limit behaviour.

## INC-009 Approval REST API — DONE
Covers A-0006, P-0009.

Acceptance criteria
- Endpoints, Problem Details, OpenAPI, `If-Match` handling as in A-0006 and P-0009.
- OIDC with Keycloak; role checks tested (`@TestSecurity`).
- Local Keycloak in `compose-devservices.yml` as in A-0008: data in schema `keycloak` of
  the compose PostgreSQL (created by run-once `postgres-init`), kept running and kept
  across dev mode restarts like the other services; realm with roles, the
  `incident-assistant` client and one user per role imported from a checked-in file.
  Tests keep per-run Dev Services.
- Approver identity from token stored in `approval_decision`.
- Decision outside `AWAITING_APPROVAL` → 409; stale version → 412.
- Detail returns the alert, state, version, occurrences count, approval decision and the
  step history with each step's `output` as stored JSON. Typed step results are added by
  the items that define them (INC-006 to INC-008).
- Implemented before the AI steps: tests bring incidents into `AWAITING_APPROVAL` through
  the service layer; no endpoint or dev shortcut sets states.

## INC-014 Incident event stream (SSE) — DONE
Covers A-0009, P-0018. Needs INC-009 (OIDC, roles).

Acceptance criteria
- `GET /api/v1/incidents/stream` as in A-0009; `401` without authentication, `403`
  without `incident-viewer`.
- Per-instance consumer of `ops.incidents.events.v1` (own group, latest offset, no
  commits) fans out every event to all open streams of the instance, unchanged, with
  `event` and `id` set.
- Heartbeat comment at the configured interval; a client whose buffer overflows is
  disconnected without affecting others; above `max-streams` → `503` with `Retry-After`.
- Configuration `incident.stream.*` as `@ConfigMapping`, validated at startup.
- Tests: two clients receive an event published to the topic, in order; slow client
  disconnected; limit enforced; role checks (`@TestSecurity`).
- README: endpoint and configuration.

## INC-017 Alert occurrence event — DONE
Covers A-0012 (amends A-0003 and A-0007; extends P-0012). Needs INC-003 (occurrences,
outbox relay) and INC-014 (stream).

A repeated alert for an open incident is recorded as an occurrence (A-0003). The UI
dashboard (A-0010) shows the occurrences count and must see it change live, so every
recorded occurrence also publishes an `incident.occurrence-recorded` event on
`ops.incidents.events.v1`, which the stream (A-0009) forwards to the UI like state events.

Acceptance criteria
- Every occurrence written to `alert_occurrence` writes one outbox event in the same
  transaction; no occurrence event when the alert opens a new incident (that is the
  existing creation event). A rolled-back ingestion attempt (A-0003 retry) leaves neither
  row.
- `IncidentOccurrenceRecorded` record and `docs/schemas/incident-occurrence-event-v1.json`
  with the fields of A-0012: keyed by incident id, `occurredAt` is the stored occurrence
  time, `occurrenceCount` includes the new occurrence, no alert payload or free text.
  `incident.state-changed`, its record and its schema are unchanged.
- The open incident is read with `FOR UPDATE` when an alert is recorded, so concurrent
  occurrences of one incident get distinct, increasing counts; the incident row and its
  version are not changed.
- The stream (A-0009) forwards the event unchanged with `event: incident.occurrence-recorded`;
  the UI item (INC-015) updates the occurrences count from it.
- Tests: duplicate alert → occurrence row and one outbox event with exactly the schema's
  properties and valid values; new incident → no occurrence event; two concurrent
  duplicates of one incident → distinct consecutive counts; relay publishes the event keyed
  by incident id; stream client receives it with the right SSE `event` name.
- Docs: A-0012, A-0003/A-0007/A-0009/A-0010/A-0011 and P-0012 updated, README topics and
  stream.

## INC-015 Incident UI — DONE
Covers A-0010, P-0019 (amends P-0009). Needs INC-009 and INC-014.

Acceptance criteria
- React + TypeScript (Vite) app in `incident-assistant-app/src/main/webui`, built and
  served by Quinoa; pinned Node.js installed by the build; lock file committed; works in
  `quarkus:dev` with live reload.
- OIDC hybrid mode: browser login via code flow with `HttpOnly`/`Secure`/`SameSite=Strict`
  session cookie, bearer tokens still accepted for machine clients; logout ends both
  sessions. Keycloak client and one user per role in the local realm.
- CSRF: state-changing requests without `X-Requested-By` rejected with `403` (tested).
- `GET /api/v1/me` returns name and `incident-*` roles.
- Screens list, detail and decision as in A-0010, with live updates from the stream;
  `412` and `409` handled as described; decision controls only for approvers. The detail
  screen shows step outputs generically (readable key/value view, text only); typed
  views follow with INC-006 to INC-008.
- Untrusted content rendered as text only; strict Content Security Policy sent (tested).
- API types generated from the OpenAPI document; Vitest + Testing Library component
  tests run in `./mvnw verify`; one Playwright end-to-end test: login as approver,
  open an `AWAITING_APPROVAL` incident (prepared by the test through the service layer),
  approve, state changes to `APPROVED` live.
- README: how to open the UI locally, users, how the UI is built.

## INC-016 E-mail notifications — TODO
Covers A-0011, P-0020. Needs INC-009 (API, client credentials).

Acceptance criteria
- Module `notification-service` with its own schema `notification` in the incident
  database (Flyway, own history table; no second database) and the platform conventions
  (layers, DTOs, Panache repositories, ArchUnit rules). Test proves the module's
  migrations and queries touch only its schema.
- Consumer of `ops.incidents.events.v1`: event recorded, duplicate (`eventId`) and older
  version skipped, pending notifications written per rule and recipient in one
  transaction.
- Sender job with `SKIP LOCKED`, exponential backoff, max attempts, `FAILED` after the
  last attempt; safe with two instances.
- Detail lookup with client credentials (Keycloak service account, `incident-viewer`;
  client `notification-service` added to the local realm), timeout and fault tolerance;
  minimal message after the lookup's retries are exhausted.
- E-mail via `quarkus-mailer`, Qute templates (text and HTML) with the content of A-0011
  only; Mailpit in `compose-devservices.yml` for dev mode.
- Tests: duplicate event → one mail; older version → none; lookup failure → minimal mail;
  SMTP failure → retried, then `FAILED`; end to end: event on the topic → mail in the
  mock mailbox with the expected subject and link.
- README: module, configuration, how to see mails locally.

## INC-005 Knowledge base ingestion (RAG) — TODO
Covers A-0004, P-0005.

Acceptance criteria
- pgvector enabled via migration; embedding table with metadata and content hash.
- Sample `knowledge/` content: one runbook per mock service and two postmortems
  matching the mock scenarios.
- Idempotent ingestion (unchanged files skipped, changed files replaced, deleted files
  removed); embedding model id check at startup.
- Admin endpoint protected by role `knowledge-admin`.
- Retriever with service filter and minimum score; tests with a fake embedding model.

## INC-006 Step executor and triage step — TODO
Covers A-0001, P-0003, P-0007, P-0008.

Acceptance criteria
- Step executor claims `RECEIVED`/`TRIAGED`/`DIAGNOSED`/`APPROVED` incidents with lease
  and `SKIP LOCKED`, bounded concurrency from config.
- `TriageAssistant` AI service with prompts as resources (skill `ai-service-conventions`),
  structured output `TriageResult` (severity, category, affected service, summary).
- Output guardrail validates the result; one reprompt; failure → `ESCALATED`.
- Fault tolerance and token recording per P-0008.
- Tests with mocked AI service for success, invalid output, timeout.
- Incident detail API returns the typed `TriageResult`; the UI shows severity, category,
  affected service and summary (as text) instead of the generic step view.

## INC-007 Diagnosis step (bounded autonomous agent) — TODO
Covers A-0001, A-0004, A-0005, P-0006.

Acceptance criteria
- `DiagnosisAgent` AI service with knowledge search tool and read-only MCP tools only.
- Budget: max tool calls and timeout from config; exceeding → `ESCALATED` with reason.
- Structured output `Diagnosis` (hypothesis, evidence list with source references,
  confidence). Guardrail requires at least one evidence item with a source.
- ArchUnit/test asserts no action tool is reachable from any AI service.
- Tool call trace stored in the step row.
- Incident detail API returns the typed `Diagnosis`; the UI shows hypothesis, confidence
  and the evidence list with source references (as text, not links).

## INC-008 Remediation proposal — TODO
Acceptance criteria
- `RemediationAdvisor` AI service, structured output `RemediationProposal`
  (action type from allowlist, target, parameters, rationale, risk).
- Guardrail rejects actions not in the allowlist or targets not matching the triaged
  service. Option `NO_ACTION` → `ESCALATED`.
- Success → `AWAITING_APPROVAL`.
- Incident detail API returns the typed `RemediationProposal`; the UI shows action,
  target, parameters, rationale and risk next to the decision controls.

## INC-010 Action execution — TODO
Acceptance criteria
- Execution step invokes the action MCP tool through a typed client (no LLM), with an
  idempotency key from incident id + approval id.
- Success → `RESOLVED`; failure → `FAILED` with error; retry safety proven by a test
  that executes twice.
- End-to-end integration test: alert on Kafka → (mocked AI) → approval via REST →
  action executed on mock MCP → `RESOLVED` event on Kafka.

## INC-011 Recovery and timeouts — TODO
Acceptance criteria
- Scheduled job reclaims expired leases and escalates incidents whose approval timed out.
- Safe with multiple instances (test with concurrent claims).

## INC-012 Observability — TODO
Covers P-0011.

Acceptance criteria
- Tracing, metrics, JSON logging and readiness checks as in P-0011.
- Prompt id/version and token counts visible on spans and step rows.
- Short `docs/operations.md`: key metrics and how to inspect a stuck incident.

## INC-013 Evaluation suite — TODO
Covers P-0010.

Acceptance criteria
- Maven profile `-Peval`, golden dataset (≥ 10 alerts across the mock scenarios).
- Scores triage fields exactly, diagnosis via LLM-as-judge rubric, proposal by allowed
  action set. Report written to `target/eval-report.md` with prompt versions.

---

## Later (not planned yet)
- Slack notification channel (second channel of the notification service, A-0011).
- Feedback loop: rejected proposals with comments added to the eval dataset.
