# ADR-0004: Alert ingestion via Kafka

- Status: Accepted
- Date: 2026-10-18

## Context
Alerts come from monitoring systems with at-least-once delivery, duplicates and
occasionally malformed payloads. Alert storms are possible.

## Decision
- Topic `ops.alerts.v1`, JSON payload validated against a versioned schema
  (Jakarta Bean Validation on an `AlertMessage` record). Schema documented in
  `docs/schemas/alert-v1.json`.
- Idempotency: unique constraint on `(source, fingerprint)` for open incidents.
  A duplicate alert for an open incident is recorded as an occurrence, not a new incident.
- Invalid messages go to the dead-letter topic `ops.alerts.v1.dlq` with the validation
  error in headers. Processing errors after retries also go to the DLQ.
- The consumer only persists the incident in `RECEIVED` and commits; workflow steps run
  asynchronously via the step executor (ADR-0003). This keeps consumer latency low and
  decouples LLM latency from Kafka rebalances.
- Outgoing state events on `ops.incidents.events.v1`, keyed by incident id.

## Alternatives considered
- Avro + schema registry: preferable in a larger landscape; rejected here to limit
  infrastructure. JSON schema keeps the contract explicit.
- Running the whole workflow in the consumer: rejected — long LLM calls would exceed
  poll intervals.

## Consequences
- Alert storms create many `RECEIVED` incidents; the step executor's concurrency limit
  protects the LLM provider.
