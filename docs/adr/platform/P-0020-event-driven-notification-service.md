# P-0020: Event-driven notification service

- Status: Accepted
- Date: 2026-10-21

## Context
People must be told when a workflow needs them or has given up. Notification channels
(e-mail, chat) are external systems with their own outages and rate limits, and must not
slow down or break the workflow. The state events (P-0012) are delivered at least once,
can arrive more than once and carry no details. Sending a message is a side effect that
cannot be taken back.

## Decision
- Notifications are sent by a separate service that consumes the state event topic with
  its own consumer group. It does not read the workflow service's tables.
- The service stores its data in a schema of its own in the same database server and
  database as the workflow service; no second database. In production it connects as a
  database role that has rights only on its schema, so the separation is enforced by the
  database, not by convention. Consuming an event and recording what to send happen in
  one transaction:
  - the event id is stored as processed; a duplicate is skipped;
  - the last version seen per entity is stored; an older event is skipped;
  - one pending notification per matching rule and recipient is written, unique per
    event and recipient.
  The offset is committed after the transaction.
- A scheduled sender claims pending notifications (`FOR UPDATE SKIP LOCKED`), sends them
  and marks them sent. Failures are retried with exponential backoff up to a configured
  number of attempts, then marked failed and logged. Sending never happens in the
  consumer.
- Which event types and states notify whom is configuration (rules with recipient lists).
- Details for the message are fetched from the workflow service's REST API with the
  OAuth client credentials flow and a read role (P-0009), with timeout and fault
  tolerance. If the lookup still fails after its retries, a minimal message (entity id,
  state, link) is sent: being on time matters more than detail.
- Channels implement one interface. Messages are rendered from templates with escaping;
  untrusted content from the workflow (inbound payloads, LLM output) is not included, and
  links point only to a configured UI base URL.
- The service follows the platform conventions (P-0013 to P-0015, P-0010, P-0011).

## Alternatives considered
- Sending from the workflow service when the state changes: couples the workflow to
  external channels and their failures.
- Sending directly in the consumer: a slow or failing channel blocks the partition, and a
  crash between send and commit resends without any record.
- Fat events with the details: contradicts thin events (P-0012) and puts sensitive data on
  a topic with weaker access control than the API.
- Deduplication in memory: lost on restart and not shared between instances.
- A separate database per service: stronger isolation, but more to provision and back up
  for a few small tables; a schema with its own role gives the same ownership boundary.

## Consequences
- Delivery is at least once per recipient: a crash after the channel accepted a message
  and before it is marked sent resends it. The window is one message.
- A notification can arrive before the details are available through the API; the
  minimal message covers that.
- Not in scope: user-managed subscriptions, digests, escalation chains, chat channels
  (they follow as further channel implementations).
