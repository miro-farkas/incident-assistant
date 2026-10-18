# ADR-0003: Workflow state persistence and execution model

- Status: Accepted
- Date: 2026-10-18

## Context
The workflow must survive restarts, run on multiple instances, never execute an action
twice, and keep an audit trail.

## Decision
- Tables (Flyway-managed): `incident` (current state, optimistic `version`, lease
  columns), `incident_step` (one row per executed step: input/output JSON, status,
  model id, prompt id + version, token usage, duration, error), `approval_decision`,
  `outbox_event`.
- One step = one DB transaction that records the step result and the state transition.
  LLM and MCP calls happen **outside** the transaction; the result is written with an
  optimistic-lock check.
- Steps are advanced by a step executor that claims work with a lease
  (`SELECT ... FOR UPDATE SKIP LOCKED`, lease expiry), so multiple instances do not
  process the same incident.
- Execution of an approved action is idempotent: the action call carries an
  idempotency key derived from incident id + approval id, and the MCP server honours it.
- State-change events are written to `outbox_event` in the same transaction and
  published to Kafka by an outbox relay.

## Alternatives considered
- Kafka as the state store (event sourcing): rejected — adds complexity, approval
  queries become harder.
- Temporal: rejected — extra infrastructure; reconsider if workflows grow.

## Consequences
- Requires a recovery job for expired leases and approval timeouts (INC-011).
- Step history doubles as audit log and as input to evaluation datasets.
