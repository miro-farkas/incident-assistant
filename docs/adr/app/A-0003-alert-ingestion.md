# A-0003: Alert ingestion via Kafka

- Status: Accepted
- Date: 2026-10-18
- Applies: P-0004

## Context
Alerts come from monitoring systems with at-least-once delivery, duplicates and
occasionally malformed payloads. Alert storms are possible.

## Decision
- Topic `ops.alerts.v1`; payload bound to an `AlertMessage` record, schema documented in
  `docs/schemas/alert-v1.json`.
- Business key: `(source, fingerprint)`. A duplicate alert for an open incident is recorded
  as an occurrence, not a new incident; the partial unique index of A-0002 enforces it.
- Dead-letter topic `ops.alerts.v1.dlq`.
- The consumer only persists the incident in `RECEIVED` and commits; the step executor
  runs the workflow.
- Outgoing state events on `ops.incidents.events.v1`, keyed by incident id (A-0007).

## Alternatives considered
- See P-0004.

## Consequences
- Alert storms create many `RECEIVED` incidents; the step executor's concurrency limit
  protects the LLM provider.
