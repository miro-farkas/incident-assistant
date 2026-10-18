# P-0001: Hybrid AI workflow — explicit state machine with bounded autonomous steps

- Status: Accepted
- Date: 2026-10-18

## Context
Business processes supported by an LLM need auditability, predictable cost, safe
execution of side effects and, often, a human decision that may arrive minutes or hours
later. A fully autonomous agent loop holds state in its conversation, has access to all
tools at once, and is hard to test and audit. A fully scripted pipeline cannot adapt
where judgement is needed.

## Decision
- The workflow is an explicit state machine persisted in the application database.
  Allowed transitions are defined in one place in code and enforced on every change.
- Most steps are explicit, with typed inputs and outputs, and at most one LLM call each.
- Autonomy is proportional to reversibility: a step may be autonomous (an AI service
  iterating over retrieval and tools) only when its tools are read-only, and it is bounded
  by a maximum number of tool calls and a wall-clock timeout.
- Side effects run in explicit steps after the required approval, without an LLM.
- Any step failure or exceeded budget moves the workflow to a terminal failure or
  escalation state, never into a silent retry loop.

## Alternatives considered
- Fully autonomous agent (supervisor pattern): tool scoping, audit and long-lived approval
  are hard to guarantee.
- BPMN engine (Camunda 8 / Kogito): heavy runtime that distracts from the AI concerns;
  reconsider when business analysts need to model processes.
- langchain4j-agentic workflow API / Quarkus Flow as the state store: waiting for a human
  and recovery must survive restarts with the application's own schema. May be used inside
  a single step through a new ADR.

## Consequences
- Each step is unit-testable with a mocked model.
- Less flexibility for unforeseen cases; mitigated by the bounded autonomous step.
- The application owns recovery of interrupted work (see P-0003).
