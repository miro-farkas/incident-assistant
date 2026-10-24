-- Run once by the test database container, as its superuser. Mirrors the production
-- provisioning (A-0011): role notification_service owns schema notification and has no
-- rights elsewhere. Every test connection switches to this role (new-connection-sql).
create role notification_service nologin;
create schema notification authorization notification_service;

-- Stands for the tables of incident-assistant-app in the same database; the role may not
-- read it.
create table public.incident (id uuid primary key);
