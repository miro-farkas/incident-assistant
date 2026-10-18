# ADR-0008: LLM provider abstraction and resilience

- Status: Accepted
- Date: 2026-10-18

## Context
The service must run locally without paid API keys and in production against a hosted
model. LLM calls are slow, can fail and cost money.

## Decision
- Provider selected by configuration through quarkus-langchain4j. Dev profile: Ollama via
  Dev Services (chat + embedding model). Prod profile: hosted provider configured by
  environment variables. Model ids are config, not code.
- Named model configurations per step (triage may use a smaller model than diagnosis).
- Every AI service call: timeout, limited retry on transient errors only, bulkhead
  limiting concurrent LLM calls, circuit breaker. Fallback is always "fail the step"
  which escalates the incident — never a fabricated answer.
- Token usage per call is recorded (step row + metrics). A per-incident token budget
  fails the step when exceeded.

## Alternatives considered
- Hard-wiring one provider: rejected — prevents local development and evaluation of
  alternatives.

## Consequences
- Local model quality is lower; the evaluation suite runs against the production model.
