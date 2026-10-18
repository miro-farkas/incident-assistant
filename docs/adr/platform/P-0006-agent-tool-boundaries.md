# P-0006: Agent tool boundaries — read tools for the LLM, action tools for code

- Status: Accepted
- Date: 2026-10-18

## Context
An autonomous step needs data from operational systems. Actions that change those systems
can be destructive and must run only after the required approval.

## Decision
- Tools are exposed over MCP in two groups: **read** tools (query state) and **action**
  tools (change state).
- AI services are configured with an explicit allowlist that contains only read tools. A
  test asserts that no action tool is visible to any AI service.
- Action tools are invoked only by deterministic code through a typed Java client over MCP,
  never through an LLM.
- Defence in depth on the server side: action tools require a credential or scope that the
  AI services' MCP client does not have; if the MCP server cannot authorise per tool, read
  and action tools are served on separate endpoints.
- Action tools require an idempotency key and are idempotent (P-0003).
- Tool outputs are structured (JSON) and size-limited, never unbounded raw dumps.

## Alternatives considered
- One tool set with a guardrail that blocks write calls: relies on detection instead of
  removing the capability.
- Direct REST clients instead of MCP: MCP keeps read tools reusable by other agents and
  clients; actions use MCP too, for one integration surface.

## Consequences
- Test doubles of the tool server must simulate realistic failures (timeouts, partial data).
