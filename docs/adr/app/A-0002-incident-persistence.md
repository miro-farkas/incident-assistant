# A-0002: Incident persistence schema

- Status: Accepted
- Date: 2026-10-18
- Applies: P-0003, P-0013, P-0014, P-0015

## Context
The incident workflow (A-0001) needs the tables of P-0003, shaped by the entity conventions
of P-0014.

## Decision
- Flyway migration `V1__incident_schema.sql` with four tables, all with the `BaseEntity`
  columns:
  - `incident`: `source`, `fingerprint`, `state` (check constraint over all states),
    `alert_payload` (`jsonb`). Lease columns are added by INC-006 with the step executor.
  - `incident_step` (append-only): step type and status, `from_state`, `to_state`, input and
    output JSON, model and prompt metadata, token counts, timing, `reason_code`,
    `error_message`.
  - `approval_decision`: decision, approver subject, comment; `created_at` is the time of
    the decision; one decision per proposal step.
  - `outbox_event`: `sequence_number`, `event_id`, `message_key`, `event_type`, `payload`
    (`text`), `published_at`.
- `V2__alert_occurrence.sql`: `alert_occurrence` (incident id, `alert_payload` as `jsonb`),
  append-only, for repeated alerts of an open incident (A-0003), and a plain index on
  `incident (source, fingerprint)` for the open-incident lookup.
- A partial unique index on `incident (source, fingerprint)` excludes the terminal states
  of `IncidentStateMachine`, so there is at most one open incident per alert (A-0003).
- `IncidentService` is the only writer: creating an incident and recording a step result
  each write the state change, the step row and the outbox event in one transaction.

## Alternatives considered
- See P-0003.

## Consequences
- The alert payload is stored as `jsonb`, which normalises key order and whitespace, and is
  not part of the `IncidentDto` returned by `IncidentService`.
- Until the first deployment, `V1` was edited in place (no `V2` migration) when the
  BaseEntity columns were introduced; nothing had been deployed. From the first deployment
  on, applied migrations are immutable and every schema change is a new version. A local
  database created from an older `V1` must be dropped once (Flyway checksum mismatch).
