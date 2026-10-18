# incident-assistant

AI-assisted incident triage service built with Java 25 and Quarkus.

- Alerts are consumed from Kafka.
- A hybrid workflow triages the incident, lets a bounded agent diagnose it using
  RAG over runbooks/postmortems and read-only MCP tools, and proposes a remediation.
- A human approves or rejects the proposal through a REST API.
- Approved actions are executed deterministically through MCP.

See `docs/architecture.md`, `docs/adr/` and `docs/backlog.md`.

> Run/build instructions are added by backlog item INC-001.
