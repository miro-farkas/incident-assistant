# Architecture decision records

Two sets, each numbered on its own:

- **`platform/` (`P-NNNN`)** — generic decisions for a Quarkus + LangChain4j service with an
  AI-assisted workflow: threading, persistence and outbox, messaging, RAG, tool boundaries,
  prompts, resilience, REST, testing, observability, package and entity conventions. They
  name no domain concepts or backlog items, so the folder can be copied into a similar
  project as is. Decisions that do not fit the new project are superseded there by its own
  ADRs.
- **`app/` (`A-NNNN`)** — decisions specific to incident-assistant: the incident workflow,
  its tables, topics, tools, API and event. Each app ADR lists the platform ADRs it applies
  (`Applies:`).

New ADRs use `platform/template.md` (skill `write-adr`).

## Platform

| ID | Title |
|---|---|
| [P-0001](platform/P-0001-hybrid-ai-workflow.md) | Hybrid AI workflow — explicit state machine with bounded autonomous steps |
| [P-0002](platform/P-0002-imperative-on-virtual-threads.md) | Imperative code on virtual threads |
| [P-0003](platform/P-0003-workflow-state-leases-and-outbox.md) | Workflow state persistence, work leases and transactional outbox |
| [P-0004](platform/P-0004-kafka-message-ingestion.md) | Kafka message ingestion |
| [P-0005](platform/P-0005-rag-with-pgvector.md) | Retrieval-augmented generation with pgvector |
| [P-0006](platform/P-0006-agent-tool-boundaries.md) | Agent tool boundaries — read tools for the LLM, action tools for code |
| [P-0007](platform/P-0007-prompt-management-and-guardrails.md) | Prompt management and guardrails |
| [P-0008](platform/P-0008-llm-provider-and-resilience.md) | LLM provider abstraction and resilience |
| [P-0009](platform/P-0009-rest-api-conventions-and-security.md) | REST API conventions and security |
| [P-0010](platform/P-0010-testing-and-evaluation.md) | Testing and LLM evaluation strategy |
| [P-0011](platform/P-0011-observability.md) | Observability |
| [P-0012](platform/P-0012-event-message-format.md) | Event message format |
| [P-0013](platform/P-0013-layered-package-structure.md) | Layered package structure |
| [P-0014](platform/P-0014-entities-base-entity-and-dtos.md) | Logic-free entities, BaseEntity and DTOs |
| [P-0015](platform/P-0015-data-access-with-panache-repositories.md) | Data access with Panache repositories |
| [P-0016](platform/P-0016-local-development-infrastructure.md) | Local development infrastructure |
| [P-0017](platform/P-0017-manual-testing-tools.md) | Manual testing tools |
| [P-0018](platform/P-0018-live-updates-via-server-sent-events.md) | Live updates to browser clients via Server-Sent Events |
| [P-0019](platform/P-0019-web-ui-served-by-the-service.md) | Web UI served by the service |
| [P-0020](platform/P-0020-event-driven-notification-service.md) | Event-driven notification service |

## App

| ID | Title | Applies |
|---|---|---|
| [A-0001](app/A-0001-incident-workflow.md) | Incident workflow | P-0001 |
| [A-0002](app/A-0002-incident-persistence.md) | Incident persistence schema | P-0003, P-0013, P-0014, P-0015 |
| [A-0003](app/A-0003-alert-ingestion.md) | Alert ingestion via Kafka | P-0004 |
| [A-0004](app/A-0004-knowledge-base.md) | Knowledge base of runbooks and postmortems | P-0005 |
| [A-0005](app/A-0005-ops-tools-mcp-server.md) | Ops tools MCP server | P-0006 |
| [A-0006](app/A-0006-approval-api.md) | Human approval REST API | P-0009 |
| [A-0007](app/A-0007-incident-state-event.md) | Incident state event | P-0012 |
| [A-0008](app/A-0008-local-development-services.md) | Local development services | P-0016, P-0010 |
| [A-0009](app/A-0009-incident-event-stream.md) | Incident event stream | P-0018, P-0009 |
| [A-0010](app/A-0010-incident-ui.md) | Incident UI | P-0019, P-0018, P-0009 |
| [A-0011](app/A-0011-notification-service.md) | Notification service | P-0020, P-0012, P-0009, P-0016 |

## Former numbering

Before the split, ADRs were numbered `ADR-0001` … `ADR-0014` in one sequence.

| Former | Now |
|---|---|
| ADR-0001 | P-0001 + A-0001 |
| ADR-0002 | P-0002 |
| ADR-0003 | P-0003 + A-0002 |
| ADR-0004 | P-0004 + A-0003 |
| ADR-0005 | P-0005 + A-0004 |
| ADR-0006 | P-0006 + A-0005 |
| ADR-0007 | P-0007 |
| ADR-0008 | P-0008 |
| ADR-0009 | P-0009 + A-0006 |
| ADR-0010 | P-0010 |
| ADR-0011 | P-0011 |
| ADR-0012 | P-0012 + A-0007 |
| ADR-0013 | P-0013 |
| ADR-0014 | P-0014 (+ schema notes in A-0002) |
