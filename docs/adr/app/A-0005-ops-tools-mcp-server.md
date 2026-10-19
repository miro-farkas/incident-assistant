# A-0005: Ops tools MCP server

- Status: Accepted
- Date: 2026-10-18 (separation mechanism, endpoints and module layout refined 2026-10-19 with INC-004)
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

### Separation of read and action tools
quarkus-mcp-server 2.0 (`quarkus-mcp-server-http`, managed by the Quarkus platform BOM)
can serve several named MCP servers on separate HTTP paths, and it checks Quarkus
security annotations on each tool call. Both are used:

- Two endpoints (Streamable HTTP; legacy SSE below each path at `/sse`):

  | MCP server | Path | Tools | Access |
  |---|---|---|---|
  | `read` | `/mcp/read` | read tools | open |
  | `actions` | `/mcp/actions` | action tools | role `ops-actions` |

  The default server (`/mcp`) is disabled. The AI services' MCP client is configured with
  `/mcp/read` only, so action tools are not even listed to the agent.
- The paths `/mcp/actions` and `/mcp/actions/*` require role `ops-actions` through a Quarkus
  HTTP permission. A request without the role gets HTTP 401 before MCP sees it.
- `ActionTools` is annotated with `@RolesAllowed("ops-actions")`, so the role is checked
  again on every call. If the path policy were misconfigured, a call would fail with MCP
  error `-32005` (security error). A test switches the path policy off to prove this.
- Group membership is declared once, in `ToolGroups`. A test asserts that each endpoint
  lists exactly its group.

### Credential for action tools
- Clients of `/mcp/actions` send an API key in header `X-Api-Key`. A custom
  `HttpAuthenticationMechanism` compares it in constant time with `ops.actions.api-key`
  (environment `OPS_ACTIONS_API_KEY`, at least 32 characters, required in prod; dev and test
  profiles set fixed non-secret values).
- A matching key yields principal `ops-actions-client` with role `ops-actions`. A wrong key
  is rejected with 401. A request without the header stays anonymous, and the path policy
  then rejects it with 401 and `WWW-Authenticate: ApiKey header="X-Api-Key"`.
- The HTTP security SPI is reactive, so this mechanism returns Mutiny `Uni`s; this is the
  only exception to P-0002 in the module.

### Tools
- Results are structured content (JSON from Java records) and are also sent as text for
  clients that read only text content.
- Arguments are validated with Bean Validation constraints (`quarkus-mcp-server-hibernate-validator`).
  The constraints appear in the input schema, and a violation is a tool error
  (`isError: true`) naming the argument. Domain failures (unknown service, unavailable
  backend, idempotency conflict) are tool errors with a message for the caller, never a
  stack trace.
- Read tools are bounded:

  | Tool | Limit |
  |---|---|
  | `get_service_metrics` | window 5-1440 min, step 1-60 min, at most 120 points (newest kept, `truncated`) |
  | `search_logs` | 1-1440 min back, `limit` 1-100 (default 20, `truncated`, `totalMatches`), messages cut at 500 characters (`messageTruncated`) |
  | `get_recent_deployments` | `limit` 1-20 (default 5, `truncated`) |
  | `get_service_health` | fixed size |

- Action tools require `idempotencyKey` (8-128 characters of `[A-Za-z0-9._:-]`, starting
  with a letter or digit).
  - The first call executes and stores the result. Repeated calls with the same key and the
    same action and parameters return that result with `replayed: true`.
  - The same key with a different action or parameters is a tool error.
  - Calls that fail (unknown service, unavailable backend) store nothing and can be retried
    with the same key.
  - Results have status `COMPLETED` or `REJECTED` (parameters do not fit the service, for
    example a rollback target that is not an earlier release). Both are stored.
  - Records are kept in memory for `ops.actions.idempotency-retention` (default 24 h) and
    capped at `ops.actions.max-idempotency-records` (default 10000). A full store refuses new
    actions rather than dropping unexpired records.
  - Records are lost on restart, which is acceptable for a mock.

### Fake data
- The built-in catalogue has `checkout`, `payments` and `inventory`, each with dependencies,
  replicas and a release history.
- Each service is configured with a scenario (`ops.services.<name>.scenario`: `healthy`,
  `bad-deployment`, `memory-leak`, `downstream-timeout`) and a fault (`ops.services.<name>.fault`:
  `none`, `unavailable`, `slow`).
  - Defaults: checkout → bad deployment, payments → downstream timeout, inventory → memory
    leak.
  - Configuring a service that is not in the catalogue fails startup.
- Data is computed from the scenario and an anchor time only, with no randomness.
  - `ops.fake-data.anchor` fixes the anchor for tests.
  - Without it, the timeline ends at the current minute.
- Actions are recorded but do not change the simulated timeline.

### Module layout
P-0013 applied to this module, which has no persistence:

| Package | Contains |
|---|---|
| `tool` | MCP tool classes (entry points, no logic) and `ToolGroups` |
| `security` | The API key authentication mechanism |
| `service` | Catalogue, scenario timeline, read and action services, idempotency store |
| `dto` | Records and enums returned by tools |
| `exception` | Domain failures reported as tool errors |
| `config` | `@ConfigMapping`, scenario and fault enums, clock producer |

Allowed dependencies:
- `tool` → `service`, `dto`, `exception`, `security`.
- `security` → `config`.
- `service` → `dto`, `exception`, `config`.

Only `tool` uses the MCP API. These rules are enforced by `LayerRulesTest`.

## Alternatives considered
- See P-0006.
- One endpoint with per-tool `@RolesAllowed` only: the agent's client would connect to the
  endpoint that also serves action tools, and the boundary would rest on one annotation.
- OIDC client credentials (Keycloak) for the action endpoint: production-like, but it adds
  an identity provider to a mock backend before the approval API needs one. Revisit when a
  real operations backend replaces the mock.
- HTTP Basic with users in a properties file: a password in configuration with no
  advantage over an API key.

## Consequences
- The mock server simulates realistic failures for tests: an unavailable backend, a slow
  backend (`ops.faults.slow-delay`, default 5 s), bounded and truncated data.
- The execution step's typed client (INC-010) must send `X-Api-Key` and use
  `/mcp/actions`. The diagnosis agent's MCP client (INC-007) uses `/mcp/read` without a key.
- Idempotency is per server instance; running several instances of the mock would need a
  shared store, which is out of scope.
