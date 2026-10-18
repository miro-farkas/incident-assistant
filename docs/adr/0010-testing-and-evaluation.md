# ADR-0010: Testing and LLM evaluation strategy

- Status: Accepted
- Date: 2026-10-18

## Context
Deterministic code must be tested conventionally; LLM behaviour must be measured, not
asserted with exact strings.

## Decision
- Unit tests (JUnit 5, AssertJ, Mockito) for domain, state machine, guardrails, mappers.
- `@QuarkusTest` integration tests with Dev Services (Kafka, PostgreSQL) and **mocked AI
  services** (`@InjectMock`) — no real LLM in `./mvnw verify`.
- MCP server tested with its own `@QuarkusTest` suite; the app's MCP client tested against
  the real mock server in an integration test.
- Architecture rules with ArchUnit (layer dependencies and entity/DTO rules per ADR-0013 and
  ADR-0014, no write tool in AI services, no logic in resources).
- Evaluation suite in a separate Maven profile `-Peval`: a golden dataset of alerts with
  expected triage fields and acceptable action sets under `src/eval/resources`. Run
  against a real model; produces a report with pass rate per step and prompt version.
  Not part of default CI; required before merging prompt changes.

## Consequences
- Eval dataset must be maintained alongside prompts.
