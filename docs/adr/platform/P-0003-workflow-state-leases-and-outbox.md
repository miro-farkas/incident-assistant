# P-0003: Workflow state persistence, work leases and transactional outbox

- Status: Accepted
- Date: 2026-10-18

## Context
A workflow (P-0001) must survive restarts, run on multiple instances, never execute a side
effect twice, keep an audit trail, and publish its state changes reliably.

## Decision
- Workflow state lives in Flyway-managed tables: one row per workflow instance (current
  state, optimistic `version`, lease columns) and one append-only row per executed step
  (input/output JSON, status, model id, prompt id and version, token usage, duration,
  error).
- One step = one database transaction that records the step result and the state
  transition. LLM and external calls happen **outside** the transaction; the result is
  written with an optimistic-lock check against the version the step started from.
- Steps are advanced by a worker that claims work with a lease
  (`SELECT … FOR UPDATE SKIP LOCKED`, lease owner = instance id, lease expiry), so multiple
  instances never process the same workflow at the same time.
- Side effects are idempotent: each call carries an idempotency key derived from stable
  ids (workflow id + decision id), and the receiving system honours it.
- **Transactional outbox:** state-change events are written to an `outbox_event` table in
  the same transaction as the change and published to the broker by a relay. The table has
  an identity `sequence_number` that orders events across instances (see P-0014); the
  payload is stored as `text` so the relay publishes exactly the bytes written (P-0012).

## Alternatives considered
- Broker as the state store (event sourcing): more complex, and queries for human
  decisions become harder.
- Temporal or a similar workflow engine: extra infrastructure; reconsider if workflows
  grow in number and length.

## Consequences
- A recovery job reclaims expired leases and times out waits for human decisions.
- Step history doubles as audit log and as input to evaluation datasets (P-0010).
- `sequence_number` is assigned at insert, not commit: it has gaps and is not a global
  commit order. Per workflow instance it increases, because writes to one instance are
  serialised by its row lock and version check; that is the order consumers can rely on.
