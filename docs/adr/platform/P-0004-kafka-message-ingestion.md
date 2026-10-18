# P-0004: Kafka message ingestion

- Status: Accepted
- Date: 2026-10-18

## Context
Inbound messages from other systems arrive with at-least-once delivery, duplicates and
occasionally malformed payloads, sometimes in bursts. Processing them may involve slow LLM
calls.

## Decision
- Topics are versioned in their name (`<domain>.<entity>.v1`). Payloads are JSON, bound to
  a record validated with Jakarta Bean Validation; the schema is documented in
  `docs/schemas/<name>-v1.json`.
- Consumers are idempotent: a business key with a database unique constraint identifies
  duplicates, which are recorded, not processed again.
- Invalid messages go to a dead-letter topic (`<topic>.dlq`) with the validation error in
  headers. Processing errors that remain after retries also go to the DLQ.
- The consumer only persists the new work item and commits; the workflow runs
  asynchronously through the lease-based worker (P-0003). This keeps consumer latency low
  and decouples LLM latency from Kafka rebalances.
- Outgoing events are keyed by the id of the entity they describe (P-0012).

## Alternatives considered
- Avro + schema registry: preferable in a larger landscape; rejected to limit
  infrastructure. A JSON schema in the repository keeps the contract explicit.
- Running the whole workflow in the consumer: long LLM calls exceed poll intervals.

## Consequences
- A burst of messages creates many pending work items; the worker's concurrency limit
  protects the LLM provider.
