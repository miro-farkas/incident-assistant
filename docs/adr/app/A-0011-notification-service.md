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

## Implementation (INC-016)
- Packages (P-0013, decided with INC-016): `client` holds the REST client of the incident API
  and its response record, `channel` the `NotificationChannel` interface and `EmailChannel`.
  Both are used only by `service`; the ArchUnit layer rules of the module include them.
- Service and severity: from the triage result of the detail (`triage.affectedService`,
  `triage.severity`, A-0014), marked "triaged"; before an incident is triaged, the ones the
  alert reported (`alert.service`, `alert.severity`), marked "as reported by the alert". Alert values
  are untrusted: control characters are replaced, values are cut at 100 characters, the HTML
  template escapes them. A missing value, and every value of the minimal message, is
  `unknown`.
- Deduplication by version only (P-0020 as amended): a redelivered state event has the
  version the cursor already holds and is skipped like an older one; there is no
  `processed_event` table. Occurrence events (A-0012) keep the incident version unchanged
  and are therefore never recorded (see Consequences).
- Tables follow P-0014: every table has the `BaseEntity` columns with `id` as primary key, so
  `incident_cursor.incident_id` is a unique key. `notification` also stores
  `incident_id`, `rule`, `state`, `reason_code` and `occurred_at` of its event, so sending
  does not depend on the event again.
- Rules: `notification.rules.<name>.state`, `.recipients` (name of a list in
  `notification.recipients`) and `.subject` (a Qute template with `service`, `severity`,
  `alertName`, `state`, `reasonCode`). Startup fails on an unknown list or two rules for one
  state; recipients of a list are de-duplicated. Body templates are shared:
  `templates/notification/message.txt` and `.html`.
- Consumer: group `notification-service`, `auto.offset.reset=latest` (a first deployment does
  not notify about past events), offset committed after the recording transaction. Recording
  is retried (fault tolerance). Events of one incident are compared under the cursor's row
  lock; two first events of one incident at once collide on the cursor's unique key, and
  the retry then compares against the committed one. An unreadable event is skipped with a warning;
  when recording still fails after its retries the channel stops and readiness reports it.
- Sender: claims due rows with `FOR UPDATE SKIP LOCKED` in a short transaction, counts the
  attempt and pushes `next_attempt_at` out by `notification.sender.claim-timeout` (default
  5 min) as a lease; then looks up the details, renders, sends and records the outcome, each
  outside that transaction, so no transaction is open while the API or SMTP is called. A
  crashed sender's rows are claimed again after the lease. Backoff before attempt *n* + 1 is
  `backoff-base * 2^(n-1)`.
- The search path of the module's connections is `notification` only. Tests connect as the
  production role: the test database's init script creates `notification_service` owning
  schema `notification` and a table in `public`, and every connection runs
  `set role notification_service`, so a statement outside the schema fails the build.

## Alternatives considered
- See P-0020. Putting the alert summary in the mail: it is attacker-controllable text in a
  message from a trusted sender.

## Consequences
- `incident-assistant-app` gets a second kind of API client (service account); its role
  checks stay as in A-0006.
- Locally both modules use the same database user; the role separation exists only in
  production and is part of its provisioning.
- The consumer handles `incident.state-changed` only and skips other event types on the
  topic, such as `incident.occurrence-recorded` (A-0012), without recording them.
- Not in scope: Slack or other chat channels, per-user subscriptions, notification UI.
