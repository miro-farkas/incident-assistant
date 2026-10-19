# A-0003: Alert ingestion via Kafka

- Status: Accepted
- Date: 2026-10-18
- Applies: P-0004

## Context
Alerts come from monitoring systems with at-least-once delivery, duplicates and
occasionally malformed payloads. Alert storms are possible.

## Decision
- Topic `ops.alerts.v1`; one firing alert per message, bound to an `AlertMessage` record,
  schema documented in `docs/schemas/alert-v1.json`: required `source`, `fingerprint`,
  `alertName`, `summary`, `startsAt`; optional `severity`, `service`, `description`,
  `labels`, `generatorUrl`, each with a length limit. Unknown properties are ignored and not
  stored; the incident keeps the validated alert serialized again. Resolved notifications
  are not part of v1.
- Messages larger than `incident.alerts.max-payload-bytes` (default 64 KiB) are rejected
  before parsing.
- Business key: `(source, fingerprint)`. A duplicate alert for an open incident is recorded
  as an occurrence in the append-only table `alert_occurrence` (incident id, alert payload,
  receive time), not as a new incident and not as a change of the incident row, so it never
  causes a version conflict with a running step. The partial unique index of A-0002 enforces
  one open incident; when two identical alerts race, the loser's insert fails on the index
  and it is recorded as an occurrence on a second attempt. Occurrences publish no event
  (A-0007).
- Dead-letter topic `ops.alerts.v1.dlq` with the reason in header `dead-letter-reason`. The
  reason names the rule and the field (for example `Alert is invalid: summary: must not be
  blank`) and never contains received content. Processing failures are retried with
  SmallRye Fault Tolerance, each attempt in a new transaction, before the record goes to the
  DLQ.
- The consumer runs on virtual threads with a configured maximum number of alerts in
  progress, below the database connection pool size. It only persists the incident in
  `RECEIVED` and commits; the step executor runs the workflow.
- Outgoing state events on `ops.incidents.events.v1`, keyed by incident id (A-0007),
  published by the outbox relay (P-0003).

## Alternatives considered
- See P-0004.
- Alertmanager webhook format (several alerts per message, shared labels): couples the
  topic to one monitoring system; an adapter can translate it into `alert-v1` messages.
- An occurrence counter on `incident`: every duplicate would increase the incident version
  and make the step executor's write fail.

## Consequences
- Alert storms create many `RECEIVED` incidents; the step executor's concurrency limit
  protects the LLM provider.
- Redelivered records after a crash become occurrences, so occurrence counts are at least,
  not exactly, the number of duplicates sent.
