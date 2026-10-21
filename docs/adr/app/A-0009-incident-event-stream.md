# A-0009: Incident event stream

- Status: Accepted
- Date: 2026-10-21
- Applies: P-0018, P-0009

## Context
The incident UI (A-0010) shows the incident list and detail live. State changes are
published as `incident.state-changed` events on `ops.incidents.events.v1` (A-0007).

## Decision
- Endpoint `GET /api/v1/incidents/stream` (`text/event-stream`) in
  `incident-assistant-app`, role `incident-viewer`.
- Incoming channel `incident-events-stream` on `ops.incidents.events.v1`, consumer group
  `incident-assistant-stream-${incident.instance.id}`, `auto.offset.reset=latest`, no
  offset commits, value read as a string and forwarded unchanged.
- Each event becomes one SSE message: `event: incident.state-changed`, `id: <eventId>`,
  `data: <event JSON>`.
- Configuration (`incident.stream.*`, validated at startup):

  | Property | Default | Meaning |
  |---|---|---|
  | `heartbeat-interval` | `15s` | interval of the heartbeat comment |
  | `client-buffer-size` | `256` | events buffered per client before it is disconnected |
  | `max-streams` | `200` | concurrent streams per instance; above it `503` with `Retry-After` |

- The fan-out from the consumer to the connected clients lives in `messaging` (consumer)
  and `service` (registry of open streams); the resource only maps the stream to HTTP
  (P-0013).

## Alternatives considered
- See P-0018. Reusing the outbox relay to also push to clients: only the instance that
  holds the relay lock would see the events.

## Consequences
- The stream carries exactly the A-0007 fields; anything else is fetched through
  `GET /api/v1/incidents/{id}`.
- Readiness includes the new channel.
