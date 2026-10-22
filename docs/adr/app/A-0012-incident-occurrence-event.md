# A-0012: Incident occurrence event

- Status: Accepted
- Date: 2026-10-22
- Applies: P-0012, P-0003
- Amends: A-0003 (occurrences publish an event), A-0007 (more than one event type on the topic)

## Context
A repeated alert for an open incident is recorded as an occurrence (A-0003). The UI (A-0010)
shows the occurrences count and must see it change live, but occurrences publish no event and
the stream (A-0009) only forwards what is on `ops.incidents.events.v1`. An occurrence is not a
state change and does not change the incident row, so `incident.state-changed` (A-0007) does
not describe it.

## Decision
- Every occurrence writes one event `incident.occurrence-recorded` to the outbox in the
  transaction that inserts the `alert_occurrence` row. Published on `ops.incidents.events.v1`,
  keyed by incident id, so it stays in order with the incident's state events.
- Fields, following the P-0012 envelope:

  | Field | Type | Notes |
  |---|---|---|
  | `schemaVersion` | integer | `1` |
  | `eventId` | UUID | unique per event |
  | `eventType` | string | `incident.occurrence-recorded` |
  | `occurredAt` | ISO-8601 UTC timestamp | the occurrence's `createdAt` (receive time) |
  | `incidentId` | UUID | same as the key |
  | `incidentVersion` | integer | current incident version; an occurrence does not change it |
  | `occurrenceId` | UUID | the `alert_occurrence` row |
  | `occurrenceCount` | integer | occurrences of the incident including this one |

- No alert payload or other free text (A-0007 rule). Schema:
  `docs/schemas/incident-occurrence-event-v1.json`, versioned on its own;
  `incident-event-v1.json` keeps describing `incident.state-changed` only.
- Recording an occurrence reads the open incident with `SELECT … FOR UPDATE`, so occurrences of
  one incident are recorded one after another and `occurrenceCount` is exact for the rows
  committed. The lock does not change the incident row or its version, so a running step's
  write never fails because of it; it only waits while a step's transaction holds the row. The
  wait is bounded by the statement timeout of the datasource.
- Consumers of `ops.incidents.events.v1` select events by `eventType` and ignore types they do
  not handle.

## Alternatives considered
- Reuse `incident.state-changed` with `previousState` equal to `state`: consumers cannot tell it
  from a transition, and it carries neither the occurrence nor the count.
- A separate topic for occurrence events: loses the ordering with the incident's state events
  and gives the stream a second consumer.
- No lock, count documented as approximate: two concurrent occurrences of one incident can both
  report the same count; the UI would show a lower number than the detail endpoint.
- An occurrence counter on `incident`: rejected in A-0003 (version conflicts with running steps).

## Consequences
- The stream (A-0009) forwards both event types unchanged; the UI (A-0010) updates the
  occurrences count from `occurrenceCount`, keeping the highest value seen, because a
  redelivered event may arrive after a newer one.
- The notification consumer (A-0011) handles `incident.state-changed` only.
- Occurrence counts stay "at least" the number of duplicates sent (A-0003, redelivery).
- Not in scope: an event when an incident is created from an alert (that is the existing
  creation state event), events for other non-state changes.
