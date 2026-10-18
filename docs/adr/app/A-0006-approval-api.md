# A-0006: Human approval REST API

- Status: Accepted
- Date: 2026-10-18
- Applies: P-0009

## Context
A human must approve or reject the proposed remediation. The UI comes later; the API must
already be safe to expose.

## Decision
- Endpoints under `/api/v1/incidents`:
  - `GET /` — list, filter by state, paging.
  - `GET /{id}` — incident detail including step history (diagnosis evidence, proposal).
  - `POST /{id}/decision` — body `{decision: APPROVE|REJECT, comment}`; requires
    `If-Match` with the incident version.
- Roles: `incident-viewer` (read), `incident-approver` (decide). The approver identity is
  stored in `approval_decision`.
- A decision is accepted only in `AWAITING_APPROVAL`; otherwise `409 Conflict`.
- The approval timeout (config) escalates the incident.

## Alternatives considered
- See P-0009.

## Consequences
- The future approval UI uses the same API and OIDC client.
