# A-0009: Incident event stream

- Status: Accepted
- Date: 2026-10-21
- Applies: P-0018, P-0009

## Context
The incident UI (A-0010) shows the incident list and detail live. State changes are
published as `incident.state-changed` events on `ops.incidents.events.v1` (A-0007).

## Decision
- Endpoint `GET /api/v1/incidents/stream` (`text/event-stream`) in
  `incident-assistant-app`, role `incident-viewer` or `incident-approver` (both may read,
  A-0006).
- Incoming channel `incident-events-stream` on `ops.incidents.events.v1`, consumer group
  `incident-assistant-stream-${incident.instance.id}`, `auto.offset.reset=latest`, no
  offset commits, value read as a string and forwarded unchanged. An event without a
  readable `eventId` and `eventType` is skipped with a warning (payload not logged); the
  channel never stops on it.
- Each event becomes one SSE message: `event: incident.state-changed`, `id: <eventId>`,
  `data: <event JSON>`. Heartbeats are the comment line `: heartbeat`.
- Configuration (`incident.stream.*`, validated at startup):

  | Property | Default | Meaning |
  |---|---|---|
  | `heartbeat-interval` | `15s` | interval of the heartbeat comment |
  | `client-buffer-size` | `256` | events buffered per client before it is disconnected |
  | `max-streams` | `200` | concurrent streams per instance; above it `503` with `Retry-After` |
  | `retry-after` | `5s` | value of `Retry-After` on that `503`, in whole seconds |

- The fan-out from the consumer to the connected clients lives in `messaging`
  (`IncidentEventStreamConsumer`) and `service` (`IncidentStreamService`: one serialized
  broadcast, a bounded buffer per stream, a counter for the limit); the resource only maps
  the stream to SSE and adds the heartbeats (P-0013).

## Alternatives considered
- See P-0018. Reusing the outbox relay to also push to clients: only the instance that
  holds the relay lock would see the events.

## Consequences
- The stream carries exactly the A-0007 fields; anything else is fetched through
  `GET /api/v1/incidents/{id}`.
- Readiness includes the new channel.
