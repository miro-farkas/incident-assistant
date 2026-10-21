# A-0011: Notification service

- Status: Accepted
- Date: 2026-10-21
- Applies: P-0020, P-0012, P-0009, P-0016

## Context
Approvers must learn that a proposal waits for them, and the on-call team that the
workflow escalated an incident. Incident state changes arrive on
`ops.incidents.events.v1` (A-0007).

## Decision
- New module `notification-service` (package `dev.mfa.incident.notification`), a Quarkus
  service of its own, consumer group `notification-service`.
- Rules (configuration `notification.rules.*`):

  | Trigger (`state`) | Recipients | Subject |
  |---|---|---|
  | `AWAITING_APPROVAL` | `notification.recipients.approvers` | `[incident-assistant] Approval needed: <service> (<severity>)` |
  | `ESCALATED` | `notification.recipients.on-call` | `[incident-assistant] Escalated: <service> (<severity>) – <reasonCode>` |

- Schema `notification` in the incident database, no second database. Flyway manages it
  with its own history table in that schema (`quarkus.flyway.schemas=notification`, which
  also creates the schema); Hibernate uses it as default schema. The module never reads
  the `public` schema of `incident-assistant-app`. In production it connects as role
  `notification_service`, which owns `notification` and has no rights on other schemas.
  Tables:
  - `processed_event` (`event_id` PK, `incident_id`, `incident_version`, `processed_at`);
  - `incident_cursor` (`incident_id` PK, `last_version`);
  - `notification` (`id`, `event_id`, `channel`, `recipient`, `status`
    `PENDING|SENT|FAILED`, `attempts`, `next_attempt_at`, `last_error`, `sent_at`;
    unique `event_id, recipient`).
- Details from `GET /api/v1/incidents/{id}` of `incident-assistant-app` with Keycloak
  client `notification-service` (confidential, service account with role
  `incident-viewer`), REST client with timeout, retry and circuit breaker.
- Message content: triaged service and severity, alert name, state, reason code, time,
  and a link `<notification.ui-base-url>/incidents/<id>`. No alert summary or
  description, no LLM output, no comments.
- Channel `EMAIL` via `quarkus-mailer` with Qute templates (plain text and HTML, escaped).
  SMTP settings from environment variables in prod.
- Local development (P-0016, A-0008): Mailpit in `compose-devservices.yml` (SMTP and web
  UI); the module discovers the compose PostgreSQL like `incident-assistant-app` and works
  in its schema. Tests use the mailer's mock mailbox and per-run Dev Services.
- Sender configuration (`notification.sender.*`): interval, batch size, max attempts
  (default 5), backoff base (default 30 s).

## Alternatives considered
- See P-0020. Putting the alert summary in the mail: it is attacker-controllable text in a
  message from a trusted sender.

## Consequences
- `incident-assistant-app` gets a second kind of API client (service account); its role
  checks stay as in A-0006.
- Locally both modules use the same database user; the role separation exists only in
  production and is part of its provisioning.
- Not in scope: Slack or other chat channels, per-user subscriptions, notification UI.
