# P-0011: Observability

- Status: Accepted
- Date: 2026-10-18

## Decision
- OpenTelemetry tracing across message consume → workflow worker → LLM → MCP → message
  produce. Span attributes: business id of the workflow instance, step, model id, prompt
  id and version, token counts. Prompt and completion content are not exported by default.
- Micrometer metrics: workflow instances by state, step duration and failure rate,
  escalation rate, LLM tokens and latency per model, time waiting for human decisions.
- JSON logging in prod with the business id in the MDC.
- Health checks: liveness, and readiness including the database, Kafka and MCP server
  reachability.
