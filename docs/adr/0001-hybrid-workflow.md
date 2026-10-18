# ADR-0001: Hybrid workflow — explicit state machine with one bounded autonomous step

- Status: Accepted
- Date: 2026-10-18

## Context
Incident handling requires auditability, predictable cost, safe execution of remediation
actions and a human approval that may arrive minutes or hours later. A fully autonomous
agent loop holds state in its conversation, has access to all tools at once, and is hard
to test and audit. A fully scripted pipeline cannot adapt investigation to the incident.

## Decision
- The workflow is an explicit state machine (see `docs/architecture.md`) persisted in
  PostgreSQL. Allowed transitions are defined in one place in code and enforced on every
  change.
- Triage, remediation proposal, approval and execution are explicit steps with typed
  inputs and outputs.
- Diagnosis is the only autonomous step: an AI service that may iterate over RAG and
  read-only MCP tools, bounded by a maximum number of tool calls and a wall-clock timeout.
- Any step failure or exceeded budget moves the incident to `ESCALATED`, never to a
  silent retry loop.

## Alternatives considered
- Fully autonomous agent (supervisor pattern): rejected — tool scoping, audit and
  long-lived approval are hard to guarantee.
- BPMN engine (Camunda 8 / Kogito): rejected for this project — heavy runtime, distracts
  from AI concerns, Java 25 support risk. Would be reconsidered if business analysts
  needed to model processes.
- langchain4j-agentic workflow API / Quarkus Flow: rejected as the primary state store —
  approval waiting and recovery must survive restarts with our own schema. May be used
  inside a step later via a new ADR.

## Consequences
- Each step is unit-testable with a mocked model.
- Less flexibility for unforeseen incident types; mitigated by the autonomous diagnosis.
- We own recovery logic (INC-011).
