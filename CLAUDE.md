# CLAUDE.md — incident-assistant

AI-assisted incident triage service. Alerts arrive via Kafka, a hybrid workflow
(explicit steps + one bounded autonomous step) diagnoses them using RAG and MCP tools,
and a human approves any remediation action through a REST API before it is executed.

Start every session by reading `docs/architecture.md`, the ADRs in `docs/adr/`,
and `docs/backlog.md`.

## Hard rules

- **Never** run `git commit`, `git push`, create branches, or open PRs. The maintainer
  does all version control manually. Only change files in the working tree.
- Implement **one backlog item at a time** (see skill `implement-backlog-item`).
  Do not start the next item without being asked.
- Everything in the repository is written in **English**: code, comments, docs, commit
  message suggestions, log messages, prompts.
- Prefer fewer features done to production quality over more features done partially.
  No TODO stubs, no commented-out code, no "simplified for demo" shortcuts unless an
  ADR explicitly allows it.
- Any decision not covered by an existing ADR that affects architecture, a dependency,
  a security boundary, or data format requires a new ADR (skill `write-adr`).
  Propose it and stop; do not implement against an unaccepted ADR.
- Do not add dependencies outside the Quarkus platform BOM / Quarkiverse without stating
  why in the item summary.

## Stack

- Java 25 (use modern language features: records, sealed interfaces, pattern matching
  for switch; no preview features).
- Quarkus (latest stable platform release supporting Java 25, pinned via BOM).
- Maven multi-module build with Maven Wrapper.
- quarkus-langchain4j (AI services, RAG, MCP client), quarkus-mcp-server (mock ops tools).
- PostgreSQL + pgvector, Flyway, Hibernate ORM (blocking, on virtual threads).
- SmallRye Reactive Messaging Kafka.
- OIDC (Keycloak Dev Services) for the approval API.
- OpenTelemetry + Micrometer.

## Modules

- `incident-assistant-app` — the service (ingestion, workflow, AI steps, REST API).
- `ops-tools-mcp` — mock operations backend exposed as an MCP server
  (metrics, logs, deployments, remediation actions). Deterministic fake data.

## Code conventions

- Package root: `dev.mfa.incident`. Package by feature (`ingestion`, `workflow`,
  `triage`, `diagnosis`, `remediation`, `approval`, `knowledge`, `tools`), not by layer.
- Domain types are records or classes without framework annotations where possible;
  JPA entities live in a `persistence` subpackage of their feature.
- No business logic in REST resources or Kafka consumers — they delegate to services.
- Constructor injection only. No field injection, no static mutable state.
- Configuration via `@ConfigMapping` interfaces, validated at startup. No magic numbers.
- Every external call (LLM, MCP, DB) has a timeout. LLM calls are protected with
  SmallRye Fault Tolerance as described in ADR-0008.
- Logging: structured, no PII and no full prompt/response bodies at INFO level.
- Prompts follow skill `ai-service-conventions`. MCP tools follow `mcp-tool-conventions`.

## Build and verify

- `./mvnw spotless:apply` then `./mvnw verify` must pass before an item is reported done.
- Dev mode: `./mvnw quarkus:dev -pl incident-assistant-app` (Dev Services start Kafka,
  PostgreSQL with pgvector, Keycloak and Ollama).
- Default tests must not call a real LLM. AI services are mocked in unit and integration
  tests. The evaluation suite (`-Peval`) is the only place that calls a real model.

## Definition of done (per backlog item)

1. Acceptance criteria of the item are met and covered by tests.
2. `./mvnw verify` passes.
3. Docs updated if behaviour or configuration changed (README, ADR status, backlog status).
4. Short summary for the maintainer: what changed, how to try it, suggested commit message
   (Conventional Commits), and any open questions.
