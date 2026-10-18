# Architecture overview

## Context

```mermaid
flowchart LR
    K[(Kafka: ops.alerts.v1)] --> APP[incident-assistant-app]
    APP --> PG[(PostgreSQL + pgvector)]
    APP -- MCP read tools --> MCP[ops-tools-mcp]
    APP -- MCP action tools --> MCP
    APP --> LLM[[LLM provider]]
    H[Human approver] -- REST + OIDC --> APP
    APP --> KO[(Kafka: ops.incidents.events.v1)]
```

## Workflow (hybrid)

Explicit, persisted state machine. Only the diagnosis step is autonomous, and only with
read-only tools and a step budget (A-0001, P-0006).

```mermaid
stateDiagram-v2
    [*] --> RECEIVED
    RECEIVED --> TRIAGED: triage (LLM, structured output)
    RECEIVED --> ESCALATED: triage failed
    TRIAGED --> DIAGNOSED: diagnosis (bounded agent, RAG + read-only MCP)
    TRIAGED --> ESCALATED: diagnosis failed / budget exceeded
    DIAGNOSED --> AWAITING_APPROVAL: remediation proposal (LLM, allowlisted actions)
    DIAGNOSED --> ESCALATED: no safe action proposed
    AWAITING_APPROVAL --> APPROVED: human approves
    AWAITING_APPROVAL --> REJECTED: human rejects
    AWAITING_APPROVAL --> ESCALATED: approval timeout
    APPROVED --> RESOLVED: action executed (deterministic MCP call)
    APPROVED --> FAILED: action failed
    REJECTED --> [*]
    RESOLVED --> [*]
    ESCALATED --> [*]
    FAILED --> [*]
```

| Step | Kind | LLM | Tools | Output |
|---|---|---|---|---|
| Triage | explicit | yes | none | severity, category, affected service |
| Diagnosis | **autonomous, bounded** | yes | RAG + read-only MCP | hypothesis, evidence, confidence |
| Proposal | explicit | yes | none | one action from allowlist + rationale |
| Approval | explicit | no | — | human decision |
| Execution | explicit | **no** | action MCP tool | result |

## Key principles

- Autonomy proportional to reversibility: reading is autonomous, writing requires approval.
- The LLM never calls a write tool. Execution is plain code calling MCP.
- All alert content and retrieved documents are untrusted input to the LLM.
- Every state transition is persisted, published as an event, and traceable to the
  prompt version and model that produced it.
