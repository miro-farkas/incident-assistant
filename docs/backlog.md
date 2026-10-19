# Backlog

Implement strictly in order. Each item is a vertical, shippable increment.
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

## INC-007 Diagnosis step (bounded autonomous agent) — TODO
Covers A-0001, A-0004, A-0005, P-0006.

Acceptance criteria
- `DiagnosisAgent` AI service with knowledge search tool and read-only MCP tools only.
- Budget: max tool calls and timeout from config; exceeding → `ESCALATED` with reason.
- Structured output `Diagnosis` (hypothesis, evidence list with source references,
  confidence). Guardrail requires at least one evidence item with a source.
- ArchUnit/test asserts no action tool is reachable from any AI service.
- Tool call trace stored in the step row.

## INC-008 Remediation proposal — TODO
Acceptance criteria
- `RemediationAdvisor` AI service, structured output `RemediationProposal`
  (action type from allowlist, target, parameters, rationale, risk).
- Guardrail rejects actions not in the allowlist or targets not matching the triaged
  service. Option `NO_ACTION` → `ESCALATED`.
- Success → `AWAITING_APPROVAL`.

## INC-009 Approval REST API — TODO
Covers A-0006, P-0009.

Acceptance criteria
- Endpoints, Problem Details, OpenAPI, `If-Match` handling as in A-0006 and P-0009.
- OIDC with Keycloak Dev Services; role checks tested (`@TestSecurity`).
- Approver identity from token stored in `approval_decision`.
- Decision outside `AWAITING_APPROVAL` → 409; stale version → 412.

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
- Approval UI.
- Notifications (Slack/e-mail) on `AWAITING_APPROVAL` and `ESCALATED`.
- Feedback loop: rejected proposals with comments added to the eval dataset.
