# A-0007: Incident state event

- Status: Accepted
- Date: 2026-10-18
- Applies: P-0012

## Context
Every incident state change writes an event to the outbox (A-0002); the relay publishes it
to `ops.incidents.events.v1`. Consumers are notifications, dashboards and the future UI.

## Decision
- One event type in v1: `incident.state-changed`, emitted for incident creation and every
  transition. Key: incident id (UUID string).
- Fields, following the P-0012 envelope:

  | Field | Type | Notes |
  |---|---|---|
  | `schemaVersion` | integer | `1` |
  | `eventId` | UUID | unique per event |
  | `eventType` | string | `incident.state-changed` |
  | `occurredAt` | ISO-8601 UTC timestamp | the incident's `updatedAt` (`createdAt` on creation) |
  | `incidentId` | UUID | same as the key |
  | `incidentVersion` | integer | incident version after the change |
  | `previousState` | string or null | `null` on creation |
  | `state` | string | `IncidentState` name |
  | `step` | string or null | step that caused the transition (`TRIAGE`, `DIAGNOSIS`, …) |
  | `stepId` | UUID or null | `incident_step` row, for lookup through the API (A-0006) |
  | `reasonCode` | string or null | machine-readable reason for `ESCALATED`/`FAILED`/`REJECTED`, e.g. `BUDGET_EXCEEDED` |

- No alert payload, LLM output, free text, approver identity or comment in the event.
- Schema: `docs/schemas/incident-event-v1.json`; breaking changes go to
  `ops.incidents.events.v2`.

## Alternatives considered
- See P-0012 (CloudEvents, fat events). One event type per transition (`incident.triaged`,
  `incident.escalated`, …) multiplies schemas without adding information beyond `state`.

## Consequences
- `reasonCode` values are documented as they are introduced (INC-006 onwards).
- Not in scope: events for non-state changes such as alert occurrences.
