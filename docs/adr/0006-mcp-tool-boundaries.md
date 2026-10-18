# ADR-0006: MCP tool boundaries — read tools for the agent, action tools for code

- Status: Accepted
- Date: 2026-10-18

## Context
The diagnosis agent needs operational data. Remediation actions (restart, rollback,
scale) are destructive and must only run after human approval.

## Decision
- `ops-tools-mcp` exposes two groups of tools:
  - Read: `get_service_metrics`, `search_logs`, `get_recent_deployments`,
    `get_service_health`.
  - Action: `restart_service`, `rollback_deployment`, `scale_service`.
- The diagnosis AI service is configured with an explicit allowlist containing only
  read tools. A test asserts that no action tool is ever visible to any AI service.
- Action tools are invoked only by the execution step through a typed Java client over
  MCP, never through an LLM.
- Defence in depth on the server side: action tools require a separate credential/scope
  that the agent's MCP client does not have. (Implementation mechanism to be confirmed
  in INC-004 against current quarkus-mcp-server capabilities; if separate endpoints or
  auth per tool are not supported, use two MCP server endpoints.)
- Action tools require an idempotency key and are idempotent.
- Tool outputs are size-limited and structured (JSON), never raw unbounded log dumps.

## Alternatives considered
- One tool set with an output guardrail blocking write calls: rejected — relies on
  detection instead of capability removal.
- Direct REST clients instead of MCP: rejected for read tools — MCP keeps tools reusable
  by other agents and clients. Kept MCP for actions too for one integration surface.

## Consequences
- The mock server must simulate realistic failures (timeouts, partial data) for tests.
