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

## Implementation notes (INC-002)
- Lease columns on `incident` are added by INC-006, which introduces the step executor.
- Each `incident_step` row also stores `from_state`, `to_state` and the reason code of the
  transition. Every step type runs in exactly one state (`StepType.sourceState`).
- `outbox_event.payload` is `text`, not `jsonb`, so the relay publishes exactly the bytes
  that were written (ADR-0012); a bigint identity column orders events.
- The alert payload is stored as `jsonb`, which normalises key order and whitespace, and is
  not part of the `Incident` snapshot returned by `IncidentService`.

## Alternatives considered
- Kafka as the state store (event sourcing): rejected — adds complexity, approval
  queries become harder.
- Temporal: rejected — extra infrastructure; reconsider if workflows grow.

## Consequences
- Requires a recovery job for expired leases and approval timeouts (INC-011).
- Step history doubles as audit log and as input to evaluation datasets.
