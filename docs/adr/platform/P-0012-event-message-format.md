# P-0012: Event message format

- Status: Accepted
- Date: 2026-10-18

## Context
State events published through the outbox (P-0003) are a contract for other teams
(notifications, dashboards, UIs). They need a defined, versioned shape before the first
event is written. Workflow data contains untrusted and potentially sensitive content:
inbound payloads, LLM output, user comments.

## Decision
- Kafka key: id of the entity the event describes, so its events stay in order. Value:
  UTF-8 JSON.
- Every event carries a common envelope: `schemaVersion` (integer), `eventId` (UUID,
  consumers deduplicate on it), `eventType` (`<entity>.<change>`, e.g. `order.state-changed`),
  `occurredAt` (ISO-8601 UTC), the entity id, and the entity version after the change
  (increases per entity; consumers drop events older than one already seen).
- Prefer one event type per kind of change with the new state as a field, over one type per
  transition.
- Events of one entity share a topic and key, so they stay in order, even when they are of
  different types. Each type has its own schema and version; consumers select events by
  `eventType` and ignore types they do not handle.
- Events are **thin**: ids, states and machine-readable reason codes only — no inbound
  payload, no LLM output, no free text, no personal data. Consumers fetch details through
  the REST API (P-0009), which applies access control.
- The outbox row stores key, event type and the exact JSON payload, so the relay copies
  bytes and has no mapping logic.
- The schema is documented in `docs/schemas/<event>-v<N>.json`. A test checks that the
  serialized event has exactly the documented properties and valid values.
- Compatibility within a version: only additive, optional fields; consumers ignore unknown
  fields. A breaking change gets a new topic `<topic>.v<N+1>`.

## Alternatives considered
- CloudEvents (binary mode, `ce_*` Kafka headers, supported by SmallRye Reactive
  Messaging): a recognised standard, but splits metadata into headers, makes the outbox row
  and tests carry headers as well as payload, and offers nothing while no consumer is
  CloudEvents-aware. Reconsider if events join a wider event mesh.
- Fat events with a full entity snapshot: put untrusted and sensitive content on a topic
  without per-consumer access control and couple consumers to the internal model.

## Consequences
- Consumers need an API call for details.
- Reason codes become part of the contract and are documented as they are introduced.
- Not in scope: Avro or a schema registry (P-0004), event replay.
