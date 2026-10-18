# P-0008: LLM provider abstraction and resilience

- Status: Accepted
- Date: 2026-10-18

## Context
The service must run locally without paid API keys and in production against a hosted
model. LLM calls are slow, can fail and cost money.

## Decision
- The provider is selected by configuration through quarkus-langchain4j. Dev profile:
  Ollama via Dev Services (chat and embedding model). Prod profile: hosted provider
  configured by environment variables. Model ids are config, not code.
- Named model configurations per step, so a simple step can use a smaller model.
- Every AI service call has a timeout, limited retry on transient errors only, a bulkhead
  limiting concurrent LLM calls, and a circuit breaker (SmallRye Fault Tolerance). The
  fallback is always "fail the step", which the workflow handles (P-0001) — never a
  fabricated answer.
- Token usage per call is recorded (step row and metrics). A per-workflow token budget
  fails the step when exceeded.

## Alternatives considered
- Hard-wiring one provider: prevents local development and evaluation of alternatives.

## Consequences
- Local model quality is lower; the evaluation suite runs against the production model.
