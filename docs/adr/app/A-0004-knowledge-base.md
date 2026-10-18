# A-0004: Knowledge base of runbooks and postmortems

- Status: Accepted
- Date: 2026-10-18
- Applies: P-0005

## Context
Diagnosis quality depends on organisational knowledge: runbooks per service and past
postmortems.

## Decision
- Source: Markdown files under `knowledge/` with front matter `service`, `type` (`runbook`
  or `postmortem`), `title`, `updated`.
- The admin ingestion endpoint requires role `knowledge-admin`.
- Retrieval is filtered by `service` when triage identified it, and is exposed to the
  diagnosis agent as a tool counted in its step budget (A-0001). The diagnosis must cite
  document titles in its evidence.

## Alternatives considered
- See P-0005.

## Consequences
- Each mock service of A-0005 gets a runbook, and the mock scenarios get matching
  postmortems, so diagnosis can be evaluated end to end.
