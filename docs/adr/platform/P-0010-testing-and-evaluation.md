# P-0010: Testing and LLM evaluation strategy

- Status: Accepted
- Date: 2026-10-18

## Context
Deterministic code must be tested conventionally; LLM behaviour must be measured, not
asserted with exact strings.

## Decision
- Unit tests (JUnit 5, AssertJ, Mockito) for the state machine, services, guardrails and
  mappers.
- `@QuarkusTest` integration tests with Dev Services (Kafka, PostgreSQL) and **mocked AI
  services** (`@InjectMock`) — no real LLM in `./mvnw verify`.
- An MCP server module has its own `@QuarkusTest` suite; the application's MCP client is
  tested against the real server in an integration test.
- Architecture rules with ArchUnit: layer dependencies and entity/DTO rules (P-0013,
  P-0014), no action tool reachable from an AI service (P-0006), no logic in resources.
- Evaluation suite in a separate Maven profile `-Peval`: a golden dataset of inputs with
  expected outputs or acceptable output sets per step under `src/eval/resources`. It runs
  against a real model and produces a report with the pass rate per step and prompt
  version. Not part of default CI; required before merging prompt changes.

## Consequences
- The evaluation dataset is maintained alongside the prompts.
