-- Work lease of the step executor (P-0003, A-0014): the instance that works on an incident and
-- until when. Both null when nobody does; an expired lease may be taken by any instance. The
-- columns are excluded from optimistic locking, so taking a lease leaves the version alone.
alter table incident add column lease_owner varchar(64);
alter table incident add column lease_expires_at timestamptz;

-- Work the executor can claim: incidents in a state with a step, oldest first.
create index ix_incident_claimable on incident (state, created_at);
