# ADR-0011: Observability

- Status: Accepted
- Date: 2026-10-18

## Decision
- OpenTelemetry tracing across Kafka consume → step executor → LLM → MCP → Kafka produce.
  Span attributes: incident id, step, model id, prompt id/version, token counts.
  Prompt and completion content are not exported by default.
- Micrometer metrics: incidents by state, step duration and failure rate, escalation rate,
  LLM tokens and latency per model, approval wait time.
- JSON logging in prod with incident id in MDC.
- Health checks: liveness, readiness including DB, Kafka and MCP server reachability.
