# ADR-0012: Incident state event format

- Status: Accepted
- Date: 2026-10-18

## Context
Every state transition writes an event to `outbox_event` in the same transaction
(ADR-0003); the outbox relay publishes it to `ops.incidents.events.v1` keyed by incident
id (ADR-0004). The topic is a contract for other teams (notifications, dashboards,
the future UI), so the payload needs a defined, versioned shape before the first event is
written. Incident data contains untrusted and potentially sensitive content: raw alert
payloads, LLM output, approver comments.

## Decision
- One event type in v1: `incident.state-changed`, emitted for incident creation and for
  every transition.
- Kafka key: incident id (UUID string). Value: UTF-8 JSON with this envelope:

  | Field | Type | Notes |
  |---|---|---|
  | `schemaVersion` | integer | `1` |
  | `eventId` | UUID | unique per event; consumers deduplicate on it |
  | `eventType` | string | `incident.state-changed` |
  | `occurredAt` | ISO-8601 UTC timestamp | time of the transaction |
  | `incidentId` | UUID | same as the key |
  | `incidentVersion` | integer | incident version after the change; increases per incident, lets consumers drop stale events |
  | `previousState` | string or null | `null` on creation |
  | `state` | string | `IncidentState` name |
  | `step` | string or null | step that caused the transition (`TRIAGE`, `DIAGNOSIS`, …) |
  | `stepId` | UUID or null | `incident_step` row, for lookup through the API |
  | `reasonCode` | string or null | machine-readable reason for `ESCALATED`/`FAILED`/`REJECTED`, e.g. `BUDGET_EXCEEDED` |

- Events are **thin**: no alert payload, no LLM output, no free text, no approver identity
  or comment. Consumers fetch details through the REST API (ADR-0009), which applies
  access control.
- The outbox row stores key, event type and the exact JSON payload, so the relay copies
  bytes and has no mapping logic.
- The schema is documented in `docs/schemas/incident-event-v1.json`. A test checks that
  the serialized event matches the documented properties and required fields.
- Compatibility within v1: only additive, optional fields; consumers must ignore unknown
  fields. A breaking change gets a new topic `ops.incidents.events.v2`.

## Alternatives considered
- CloudEvents (binary mode, `ce_*` Kafka headers, supported by SmallRye Reactive
  Messaging): a recognised standard, but splits metadata into headers, makes the outbox
  row and tests carry headers as well as payload, and offers no benefit while there is no
  CloudEvents-aware consumer. Reconsider if events join a wider event mesh.
- Fat events with the full incident snapshot: rejected. They put untrusted LLM output
  and alert content on a topic without per-consumer access control, and couple consumers
  to the internal model.
- One event type per transition (`incident.triaged`, `incident.escalated`, …): rejected.
  It multiplies schemas without adding information beyond `state`.

## Consequences
- Consumers need an API call for details; acceptable for notifications and dashboards.
- `reasonCode` values become part of the contract and must be documented as they are
  introduced (INC-006 onwards).
- Not in scope: Avro or a schema registry (see ADR-0004), event replay, and events for
  non-state changes such as alert occurrences.
