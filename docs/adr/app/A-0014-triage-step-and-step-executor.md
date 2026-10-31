# A-0014: Triage step and step executor

- Status: Accepted (amended with A-0015: no chat memory, guardrail retry count)
- Date: 2026-10-25
- Applies: P-0001, P-0003, P-0007, P-0008, P-0013

## Context
A-0001 defines triage as the first explicit step (in `RECEIVED`) and P-0003 the leased worker
that advances workflows. P-0008 asks for a configurable provider with a smaller model for
simple steps, timeouts, limited retries, a bulkhead, a circuit breaker, token recording and a
per-workflow token budget. The executor, the triage result and its failure handling need
concrete values; the development PC has about 4 GB of free memory and no GPU.

## Decision
- **Models** (quarkus-langchain4j, named model config `triage`, P-0008):
  - dev: Ollama `qwen2.5:3b` from the compose Ollama (A-0013), pulled on first use; LLM
    timeout 120 s, because it runs on the CPU;
  - prod: Anthropic (`quarkus-langchain4j-anthropic`), model `claude-haiku-5-5` by default,
    `ANTHROPIC_API_KEY` and `ANTHROPIC_MODEL` from the environment; timeout 30 s;
  - test: the AI service is mocked (P-0010); no model at all.
- **Packages** (P-0013 as amended): `TriageAssistant` and `TriageOutputGuardrail` in `ai`;
  the structured output `TriageResult` in `dto`, because it is also stored as the step
  output and returned by the API.
- **Lease** (migration `V4__incident_lease.sql`): `incident.lease_owner` (instance id) and
  `incident.lease_expires_at`, excluded from optimistic locking, so taking a lease does not
  change the incident version that events and decisions refer to.
- **Step executor** (`incident.workflow.executor.*`): every `interval` (2 s) it claims up to
  as many incidents as it has free slots (`max-concurrency`, default 2) whose state has a
  step implementation, with an expired or no lease, oldest first, `FOR UPDATE SKIP LOCKED`,
  and sets the lease for `lease-duration` (default 5 min, longer than an LLM call with its
  retries). Each step runs on a virtual thread outside any transaction and is recorded with
  `IncidentService.recordStep`, which also clears the lease. INC-006 implements the triage
  step only; the states `TRIAGED`, `DIAGNOSED` and `APPROVED` are claimed once their steps
  exist (INC-007, INC-008, INC-010). Expired leases are simply claimable again; the recovery
  job of INC-011 adds escalation of stuck incidents.
- **`TriageResult`** (record with Bean Validation, `@Description` per field):
  - `severity`: `CRITICAL | HIGH | MEDIUM | LOW`;
  - `category`: `DEPLOYMENT | RESOURCE_EXHAUSTION | DEPENDENCY | CAPACITY | CONFIGURATION |
    OTHER`;
  - `affectedService`: `[a-z][a-z0-9-]{0,62}`;
  - `summary`: 1-500 characters, one paragraph of plain text.
- **Prompt** `prompts/triage/` (id `triage`, version 1): system message with the shared
  safety and output rules; user message with the alert fields (name, source, service,
  severity, summary, description, labels) inside `<alert>` delimiters marked as untrusted
  data.
- **Output guardrail**: the schema rules above, and if the alert reported a service, the
  triaged service must be that service (case-insensitive). One reprompt with the violation
  (`quarkus.langchain4j.guardrails.max-retries=2`, which counts the first answer).
- **Fault tolerance** on the triage call: timeout as above, one retry on timeout or
  connection failure only, bulkhead of `max-concurrency`, circuit breaker (8 calls, 50 %
  failures, 60 s open).
- **Outcomes** (each one step row, `incident_step` with model id, prompt id and version,
  tokens, duration):
  - valid result → `TRIAGED`, output = the `TriageResult`;
  - guardrail still failing after the reprompt → `ESCALATED`, `TRIAGE_INVALID_OUTPUT`;
  - timeout, retries used up or circuit open → `ESCALATED`, `TRIAGE_UNAVAILABLE`;
  - tokens of all steps of the incident above `incident.workflow.token-budget` (default
    20 000) → `ESCALATED`, `TOKEN_BUDGET_EXCEEDED`, checked after each step.
- **API and UI**: the incident detail (A-0006) gets `triage`, the `TriageResult` of the
  latest successful triage step or `null`; the UI shows severity, category, affected service
  and summary as text instead of the generic step view (A-0010).

## Implementation (INC-006)
- Shared prompt fragments are in `src/main/resources/templates/prompts/_shared/`, because Qute
  resolves `{#include}` from `templates/`; the step prompts stay in `prompts/triage/`. A test
  renders both with the engine the AI service uses.
- Alert fields the alert lacks reach the prompt as `null` and are printed as "not reported",
  so the guardrail's service rule only applies when the alert named a service.
- Token counts of a step are optional (`ModelUsageDto`): a call that failed (guardrail after
  the reprompt, timeout) records model id and prompt version but no tokens. The model id is
  the one the provider reports, otherwise `incident.workflow.triage-model`.
- An exceeded token budget records the step as `FAILED` with its result as output.
- A step that throws unexpectedly (not a model failure) is logged; its lease expires and the
  incident is claimed again.
- The executor is off in the test profile (`incident.workflow.executor.enabled=false`), so
  tests that drive the workflow through the service layer are not raced; `TriageStepTest`
  turns it on with a mocked `TriageAssistant`.
- The ArchUnit rules of both modules anchor their packages at the module root, because
  `..model..` also matched library packages such as `dev.langchain4j.model`.
- notification-service now takes service and severity from the triage result (A-0011).
- `TriageAssistant` is `@ApplicationScoped`: the default scope of an AI service is the request,
  and the executor's threads have no request context. A test checks the scope, because the
  mocked assistant of the other tests hides it.
- The fault-tolerance keys of the triage call name the generated class
  (`dev.mfa.incident.ai.TriageAssistant$$QuarkusImpl/triage`): SmallRye reads them from the class
  that declares the method and silently ignores the interface name. `TriageFaultToleranceTest`
  checks that the configured bulkhead and timeout take effect.
- The model clients of the `triage` model do not retry (`ModelClientCustomizers`, both
  providers): langchain4j's own retry also retried a call the fault-tolerance timeout had
  interrupted. Fault tolerance is the only retry (P-0008).
- Measured in dev mode on the maintainer's CPU-only PC (`qwen2.5:3b`, one alert): 110.6 s,
  including about 35 s to load the model; 667 input and 72 output tokens, valid on the first
  answer. A warm model answers a request of this size in about 30 s.

## Alternatives considered
- A 7-8 B local model (`qwen3:8b`): better triage, but needs 5-6 GB more memory than the
  development PC has free.
- OpenAI-compatible provider in prod: works with many endpoints, but the maintainer chose
  Anthropic.
- One executor per state or a queue table: more moving parts for four step types; the lease
  on the incident row already serialises work per incident.
- Retrying a failed triage later instead of escalating: contradicts A-0001 (no silent retry
  loop).

## Consequences
- Dev mode needs the compose Ollama and pulls `qwen2.5:3b` (about 1.9 GB) once.
- Triage in dev mode takes up to a minute per incident on the CPU; quality is lower than in
  prod, which the evaluation suite (INC-013) measures against the prod model.
- Not in scope: the other steps, the recovery job (INC-011), an input guardrail beyond the
  existing alert size limits (A-0003 already bounds every field that reaches the prompt).
