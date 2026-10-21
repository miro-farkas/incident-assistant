# P-0018: Live updates to browser clients via Server-Sent Events

- Status: Proposed
- Date: 2026-10-21

## Context
A browser UI should show state changes without the user reloading. Browsers cannot read
the event topic (P-0012) directly, and the service runs as several instances, so a change
processed by one instance must reach clients connected to any instance. The UI only needs
to know that something changed; the details come from the REST API (P-0009), which
applies access control.

## Decision
- The service exposes one Server-Sent Events (SSE) endpoint per resource collection,
  `GET /api/v1/<collection>/stream`, `text/event-stream`, protected by the read role of the
  collection.
- The endpoint is fed from the event topic, not from the code that writes the change.
  Every instance consumes the topic with a consumer group of its own (derived from the
  instance id), starts at the latest offset and commits no offsets: each instance sees
  every event, in the order every other consumer sees them.
- Events are forwarded unchanged (they are thin, P-0012) as SSE messages with `event` =
  `eventType` and `id` = `eventId`. Clients treat them as invalidation hints and fetch the
  current state through the REST API. On connect and reconnect a client reloads what it
  shows; there is no replay (`Last-Event-ID` is ignored).
- Each connection has a bounded buffer. A client that falls behind is disconnected and
  reconnects, rather than slowing down other clients or the consumer.
- A comment line is sent at a configured interval as heartbeat, so proxies keep idle
  connections open and dead ones are detected.
- The number of concurrent streams per instance is limited by configuration; above it the
  endpoint answers `503 Service Unavailable` with `Retry-After`.

## Alternatives considered
- Polling the list endpoint: simple, but either slow or wasteful, and it multiplies load
  with the number of open UIs.
- WebSockets: bidirectional, which the UI does not need, and harder to pass through
  proxies and to secure with the existing authentication.
- Publishing to clients directly from the code that commits the change: clients connected
  to other instances miss the event, and a rolled-back transaction could still be pushed.
- Fat events carrying the changed data: duplicates access control in the stream and
  contradicts thin events (P-0012).

## Consequences
- The stream is best effort: an event may be missed during a reconnect, which the reload
  on reconnect covers. It is not an audit trail.
- One extra consumer group per instance on the broker; instance ids must stay stable
  (they already are, P-0003).
- Not in scope: per-entity access control in the stream (all readers of the collection
  receive all its events), replay of missed events.
