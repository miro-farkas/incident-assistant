# A-0015: Diagnosis step

- Status: Accepted
- Date: 2026-10-30
- Applies: P-0001, P-0005, P-0006, P-0007, P-0008, P-0013

## Context
A-0001 makes diagnosis the only autonomous step: in `TRIAGED`, an AI service iterates over
the knowledge base (A-0004) and the read tools of `ops-tools-mcp` (A-0005), bounded by a
maximum number of tool calls and a wall-clock timeout. P-0006 requires an explicit allowlist
of read tools and a test that no action tool is visible. The step needs a model, an MCP
client, a way to count tool calls across both tool sources, a structured result whose
evidence can be checked, and a place for the tool call trace. INC-006 left the AI services
with the default chat memory: without a request context, quarkus-langchain4j uses the memory
id `default`, so every call of an assistant shares one conversation across incidents.

## Decision
- **Chat memory**: AI services have none
  (`chatMemoryProviderSupplier = RegisterAiService.NoChatMemoryProviderSupplier.class`).
  Each call starts from its system and user message; the tool loop and the guardrail
  reprompt keep their messages for that call only. This also applies to `TriageAssistant`
  (amends A-0014).
- **Models** (named model config `diagnosis`, P-0008):
  - dev: Ollama `qwen2.5:3b`, the triage model, which supports tool calling; without
    Ollama's JSON format mode, which would force every answer, tool calls included, into
    one JSON object; the structured output comes from langchain4j's JSON schema
    instructions;
  - prod: Anthropic, model `claude-sonnet-5-5` by default (`ANTHROPIC_DIAGNOSIS_MODEL`),
    since the step plans tool calls and weighs evidence;
  - test: the AI service is mocked; agent-loop tests use a scripted chat model (below).
- **Packages** (P-0013, no amendment): `DiagnosisAgent`, `DiagnosisOutputGuardrail`, the tool
  provider and the knowledge search tool are in `ai`. The search tool depends on the
  interface `ai.KnowledgeSearch`, which `service.KnowledgeRetriever` implements, so `ai`
  still depends on `dto` and `config` only. The result record `Diagnosis` is in `dto`.
- **MCP client** (`quarkus-langchain4j-mcp`, Quarkiverse): one client `ops-read`, Streamable
  HTTP, URL `quarkus.langchain4j.mcp.ops-read.url` (prod `OPS_TOOLS_MCP_READ_URL`, required;
  dev `http://localhost:8081/mcp/read`; test an unreachable address, so a call that is not
  mocked fails), no credential header, tool execution timeout 10 s. Its readiness check is
  off: an unreachable tool server fails diagnoses, not the service (the extension's
  `McpClientHealthCheck` is a bean of its runtime jar, so besides
  `quarkus.langchain4j.mcp.health.enabled=false` it is switched off by class name). No tool
  provider is generated from the configuration.
- **Tools** (`DiagnosisTools`, P-0006): a `Supplier<ToolProvider>` named by the agent, not a
  `ToolProvider` bean, which quarkus-langchain4j would attach to every AI service; triage
  names `NoToolProviderSupplier` explicitly:
  - MCP tools filtered by name to the allowlist `get_service_metrics`, `search_logs`,
    `get_recent_deployments`, `get_service_health`, declared once in code. A new tool on the
    server is not visible until it is added there;
  - `search_knowledge(query)`: up to `incident.knowledge.retrieval.limit` segments, filtered
    by the triaged service (A-0004; the model cannot change the filter), each as
    `<document title="…" path="…" heading="…">text</document>`, marked as untrusted data;
  - listing the MCP tools fails → the step fails (`DIAGNOSIS_UNAVAILABLE`), rather than
    diagnosing without operational data.
- **Budget** (`incident.workflow.diagnosis.*`):
  - `max-tool-calls` (default 6) counts every tool call of one diagnosis, across both tool
    sources and parallel calls in one response (allowed 1-20). A call beyond it is recorded
    as `NOT_EXECUTED` and ends the agent's call at once (`ToolCallBudgetExceededException`,
    which quarkus-langchain4j does not hand to the model as a tool error); the step escalates
    with `DIAGNOSIS_TOOL_BUDGET_EXCEEDED`. The AI service's round-trip limit is a constant
    of 22 (the largest allowed budget + 2; an annotation value cannot come from
    configuration), so a looping model ends there at the latest;
  - wall-clock timeout of the whole call (fault tolerance): 120 s, dev 270 s; it must stay
    below `incident.workflow.executor.lease-duration` (5 min). Timeout →
    `DIAGNOSIS_TIMEOUT`;
  - no retry of the whole call (it would double cost and time), and the model clients do not
    retry either (`ModelClientCustomizers`, as for triage); bulkhead of the executor's
    concurrency and a circuit breaker as for triage, which a budget overrun does not trip;
    model or tool server unreachable or circuit open → `DIAGNOSIS_UNAVAILABLE`;
  - the per-incident token budget (A-0014) now defaults to 60 000, because tool results are
    sent back on every round trip.
- **Tool call budget, trace and evidence check of one run**: the step binds a `DiagnosisRun`
  to its thread with a `ScopedValue` for the duration of the call. The tool provider counts
  and records into it, and the guardrail reads it. Tools run on the calling thread.
- **`Diagnosis`** (record, Bean Validation, `@Description` per field):
  - `hypothesis`: 1-1000 characters, the most likely cause;
  - `confidence`: `HIGH | MEDIUM | LOW`;
  - `evidence`: 1-8 items of `statement` (1-500 characters), `sourceType`
    (`KNOWLEDGE | TOOL`) and `source` (1-200 characters).
- **Output guardrail**: the schema rules above; every `KNOWLEDGE` source is the title of a
  document `search_knowledge` returned in this run (A-0004 cites titles), every `TOOL`
  source is the name of a tool called successfully in this run. One reprompt with the
  violation (`quarkus.langchain4j.guardrails.max-retries=2`: langchain4j counts the first
  answer); still invalid → `DIAGNOSIS_INVALID_OUTPUT`. A valid answer is handed back as the
  message without code fence, because for a `Result` return type the AI service parses that
  message, not a result object of the guardrail.
- **Prompt** `prompts/diagnosis/` (id `diagnosis`, version 1): system message with the
  procedure, the tool budget and the shared safety and output rules; user message with the
  alert in `<alert>` and the triage result in `<triage>`, both untrusted data.
- **Tool call trace** (migration `V5__step_tool_calls.sql`): `incident_step.tool_calls jsonb`,
  null for steps without tools. One entry per requested call, in order: `tool`, `arguments`
  (as sent by the model, cut at 1000 characters), `outcome` (`OK | ERROR | NOT_EXECUTED`),
  `durationMs`, `resultChars`, and for `search_knowledge` the `titles` returned. No tool
  results are stored.
- **Outcomes**: valid diagnosis within budget → `DIAGNOSED`, output = the `Diagnosis`; the
  failures above → `ESCALATED` with their reason code; `TOKEN_BUDGET_EXCEEDED` as in A-0014.
  Every outcome records model, prompt id and version, tokens, duration and the trace.
- **Tests**: the guardrail and tool provider are unit-tested; `DiagnosisStepTest` mocks the
  agent; an agent-loop test replaces the `diagnosis` chat model with a scripted one (a CDI
  alternative in test scope) and a fake MCP client, and asserts the tools offered to the
  model are exactly the allowlist plus `search_knowledge`, the budget, the trace and the
  evidence check.
- **API and UI**: the incident detail gets `diagnosis`, the `Diagnosis` of the latest
  successful diagnosis step or `null`; the UI shows hypothesis, confidence and the evidence
  list with sources as text (A-0010).

## Alternatives considered
- Counting with quarkus-langchain4j's `maxToolCallingRoundTrips` only: it limits model round
  trips, not tool calls; one response may request several tools.
- `@McpToolBox("ops-read")` and `@ToolBox` on the agent: the simplest wiring, but the tool
  calls of the two sources cannot be counted together, and the MCP side has no name filter.
- Storing the trace inside `output`: mixes the audit trail with the typed result the API
  returns, and a failed step has no output.
- Keeping chat memory keyed by incident id and removing it after the step: works, but the
  steps need no history, and a missed removal leaks one incident into the next.
- A smaller prod model (`claude-haiku-5-5`) for diagnosis: cheaper, but the step is the one
  that needs planning across tools; the model id is configuration and can be changed.

## Consequences
- Dev mode needs `ops-tools-mcp` running (`./mvnw quarkus:dev -pl ops-tools-mcp`); without
  it, diagnoses escalate with `DIAGNOSIS_UNAVAILABLE`.
- A 3B model on a CPU is slow and weak at multi-step tool use: in dev, diagnoses take
  minutes and may escalate; the evaluation suite (INC-013) runs against the prod model.
- The trace is stored but not returned by the API or shown in the UI yet.
- The alert fields that reach the prompts are selected in one place (`service.AlertInput`)
  for both steps.
- INC-007 found that `max-retries=1` of INC-006 allowed no reprompt at all, and that a
  successful reprompt would have returned the first, invalid answer; both are fixed for
  triage as well.
- Not in scope: memory across steps of one incident, tools that read the incident history,
  retrieval re-ranking.
