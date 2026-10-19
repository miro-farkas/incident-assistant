-- Repeated alerts for an open incident (A-0003). Append-only. Recording an occurrence does
-- not touch the incident row, so it never conflicts with a step updating the incident.
create table alert_occurrence (
    id            uuid        not null,
    created_at    timestamptz not null,
    updated_at    timestamptz not null,
    version       bigint      not null,
    incident_id   uuid        not null,
    alert_payload jsonb       not null,
    constraint pk_alert_occurrence primary key (id),
    constraint fk_alert_occurrence_incident foreign key (incident_id) references incident (id)
);

create index ix_alert_occurrence_incident on alert_occurrence (incident_id, created_at);

-- Lookup of the open incident for an alert. The partial unique index only serves queries
-- whose plan can prove its predicate, which a generic plan with the excluded states as
-- parameters cannot.
create index ix_incident_source_fingerprint on incident (source, fingerprint);
