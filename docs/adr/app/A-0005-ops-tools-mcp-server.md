# A-0005: Ops tools MCP server

- Status: Accepted
- Date: 2026-10-18
- Applies: P-0006

## Context
The diagnosis agent needs operational data; remediation actions (restart, rollback,
scale) are destructive and must run only after human approval.

## Decision
- Module `ops-tools-mcp` is a mock operations backend exposed as an MCP server with
  deterministic fake data:
  - Read tools: `get_service_metrics`, `search_logs`, `get_recent_deployments`,
    `get_service_health`.
  - Action tools: `restart_service`, `rollback_deployment`, `scale_service`.
- The diagnosis AI service's allowlist contains only the read tools. Action tools are
  called only by the execution step, with an idempotency key derived from incident id and
  approval id.
- The server-side separation mechanism (per-tool authorisation or two endpoints) is
  confirmed in INC-004 against current quarkus-mcp-server capabilities and recorded here.

## Alternatives considered
- See P-0006.

## Consequences
- The mock server simulates realistic failures (timeouts, partial data) for tests.
