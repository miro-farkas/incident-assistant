-- Notification state (A-0011, P-0020) in schema notification, which Flyway creates and
-- uses as default schema; nothing here refers to another schema.
-- Every table carries the BaseEntity columns (P-0014): id (UUIDv7), created_at,
-- updated_at and version (optimistic locking).

-- Events already handled, so a redelivered event is skipped. created_at is the time it
-- was processed. Append-only.
create table processed_event (
    id               uuid        not null,
    created_at       timestamptz not null,
    updated_at       timestamptz not null,
    version          bigint      not null,
    event_id         uuid        not null,
    incident_id      uuid        not null,
    incident_version bigint      not null,
    constraint pk_processed_event primary key (id),
    constraint uq_processed_event_event_id unique (event_id)
);

-- Newest incident version seen, so an older event that arrives late is skipped.
create table incident_cursor (
    id           uuid        not null,
    created_at   timestamptz not null,
    updated_at   timestamptz not null,
    version      bigint      not null,
    incident_id  uuid        not null,
    last_version bigint      not null,
    constraint pk_incident_cursor primary key (id),
    constraint uq_incident_cursor_incident unique (incident_id)
);

-- One message per event, rule and recipient. The event fields needed for the message are
-- copied, so sending does not depend on the event again. next_attempt_at is when the
-- sender may take the row; while a sender works on it, it is pushed out by the claim
-- timeout (a lease), so a crashed sender's rows are taken again later.
create table notification (
    id              uuid          not null,
    created_at      timestamptz   not null,
    updated_at      timestamptz   not null,
    version         bigint        not null,
    event_id        uuid          not null,
    incident_id     uuid          not null,
    rule            varchar(64)   not null,
    state           varchar(32)   not null,
    reason_code     varchar(64),
    occurred_at     timestamptz   not null,
    channel         varchar(16)   not null,
    recipient       varchar(320)  not null,
    status          varchar(16)   not null,
    attempts        integer       not null,
    next_attempt_at timestamptz   not null,
    last_error      varchar(1000),
    sent_at         timestamptz,
    constraint pk_notification primary key (id),
    constraint uq_notification_event_recipient unique (event_id, recipient),
    constraint ck_notification_channel check (channel in ('EMAIL')),
    constraint ck_notification_status check (status in ('PENDING', 'SENT', 'FAILED')),
    constraint ck_notification_attempts check (attempts >= 0)
);

create index ix_notification_due on notification (next_attempt_at) where status = 'PENDING';
