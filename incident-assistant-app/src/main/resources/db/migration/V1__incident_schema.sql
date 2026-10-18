-- Workflow state (A-0002): incidents, executed steps, approval decisions and the
-- transactional outbox for state events (A-0007).
-- Every table carries the BaseEntity columns (P-0014): id (UUIDv7), created_at,
-- updated_at (both set by the database) and version (optimistic locking).

create table incident (
    id            uuid         not null,
    created_at    timestamptz  not null,
    updated_at    timestamptz  not null,
    version       bigint       not null,
    source        varchar(100) not null,
    fingerprint   varchar(200) not null,
    state         varchar(32)  not null,
    alert_payload jsonb        not null,
    constraint pk_incident primary key (id),
    constraint ck_incident_state check (state in (
        'RECEIVED', 'TRIAGED', 'DIAGNOSED', 'AWAITING_APPROVAL', 'APPROVED',
        'REJECTED', 'RESOLVED', 'ESCALATED', 'FAILED'))
);

-- At most one open incident per alert source and fingerprint (A-0003).
-- The excluded states are the terminal states of IncidentStateMachine.
create unique index ux_incident_open_fingerprint
    on incident (source, fingerprint)
    where state not in ('REJECTED', 'RESOLVED', 'ESCALATED', 'FAILED');

create index ix_incident_state on incident (state);

-- Append-only: rows are never updated, so updated_at = created_at and version = 0.
create table incident_step (
    id             uuid          not null,
    created_at     timestamptz   not null,
    updated_at     timestamptz   not null,
    version        bigint        not null,
    incident_id    uuid          not null,
    step_type      varchar(32)   not null,
    status         varchar(16)   not null,
    from_state     varchar(32)   not null,
    to_state       varchar(32)   not null,
    input          jsonb         not null,
    output         jsonb,
    model_id       varchar(200),
    prompt_id      varchar(100),
    prompt_version varchar(32),
    input_tokens   integer,
    output_tokens  integer,
    started_at     timestamptz   not null,
    finished_at    timestamptz   not null,
    duration_ms    bigint        not null,
    reason_code    varchar(64),
    error_message  varchar(2000),
    constraint pk_incident_step primary key (id),
    constraint fk_incident_step_incident foreign key (incident_id) references incident (id),
    constraint ck_incident_step_type check (step_type in ('TRIAGE', 'DIAGNOSIS', 'PROPOSAL', 'EXECUTION')),
    constraint ck_incident_step_status check (status in ('SUCCEEDED', 'FAILED')),
    constraint ck_incident_step_tokens check (
        (input_tokens is null or input_tokens >= 0) and (output_tokens is null or output_tokens >= 0)),
    constraint ck_incident_step_duration check (duration_ms >= 0)
);

create index ix_incident_step_incident on incident_step (incident_id, started_at);

-- created_at is the time of the decision.
create table approval_decision (
    id               uuid          not null,
    created_at       timestamptz   not null,
    updated_at       timestamptz   not null,
    version          bigint        not null,
    incident_id      uuid          not null,
    proposal_step_id uuid          not null,
    decision         varchar(16)   not null,
    approver_subject varchar(255)  not null,
    comment          varchar(2000),
    constraint pk_approval_decision primary key (id),
    constraint fk_approval_decision_incident foreign key (incident_id) references incident (id),
    constraint fk_approval_decision_step foreign key (proposal_step_id) references incident_step (id),
    constraint uq_approval_decision_proposal unique (proposal_step_id),
    constraint ck_approval_decision_decision check (decision in ('APPROVE', 'REJECT'))
);

create index ix_approval_decision_incident on approval_decision (incident_id);

-- Payload is stored as text: the relay publishes it byte for byte (P-0012).
-- sequence_number orders events across instances; UUIDv7 ids are only time-ordered per
-- JVM. It is assigned at insert, not at commit, so it has gaps and can commit out of order
-- across incidents; for one incident it increases, because writes to one incident are
-- serialised by its row lock and version check. event_id is the id inside the payload,
-- created before the row.
create table outbox_event (
    id              uuid         not null,
    created_at      timestamptz  not null,
    updated_at      timestamptz  not null,
    version         bigint       not null,
    sequence_number bigint generated always as identity,
    event_id        uuid         not null,
    message_key     varchar(100) not null,
    event_type      varchar(100) not null,
    payload         text         not null,
    published_at    timestamptz,
    constraint pk_outbox_event primary key (id),
    constraint uq_outbox_event_sequence unique (sequence_number),
    constraint uq_outbox_event_event_id unique (event_id)
);

create index ix_outbox_event_unpublished on outbox_event (sequence_number) where published_at is null;
