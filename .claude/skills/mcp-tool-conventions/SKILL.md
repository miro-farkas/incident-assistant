---
name: mcp-tool-conventions
description: Use when adding or changing a tool in the ops-tools-mcp server or the MCP client configuration in incident-assistant-app.
---

# MCP tool conventions (ADR-0006)

- Tool names are `snake_case` verbs (`get_service_metrics`). Descriptions are written for
  an LLM: what it returns, when to use it, limits.
- Every tool belongs to exactly one group: **read** or **action**. Group membership is
  declared in one place and covered by a test.
- Read tools: no side effects, bounded output (limit parameters with sane defaults and
  hard maxima, truncation flag in the result), structured JSON results.
- Action tools: require `idempotencyKey`; same key returns the original result without
  re-executing; validate target service exists; return a structured result with status.
- Inputs validated; invalid input returns a tool error, not an exception stack trace.
- Fake data is deterministic per configured scenario; no randomness without a fixed seed.
- Client side (app): read tools are reachable only through the diagnosis agent's tool
  allowlist; action tools only through the typed `OpsActionsClient` used by the
  execution step.
- Every tool has tests for success, invalid input, and (read tools) size limits.
