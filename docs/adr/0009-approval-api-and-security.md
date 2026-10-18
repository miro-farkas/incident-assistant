# ADR-0009: Human approval REST API and security

- Status: Accepted
- Date: 2026-10-18

## Context
A human must approve or reject the proposed remediation. UI comes later; the API must
already be safe to expose.

## Decision
- Endpoints under `/api/v1/incidents`:
  - `GET /` — list with filter by state, paging.
  - `GET /{id}` — incident detail including step history (diagnosis evidence, proposal).
  - `POST /{id}/decision` — body `{decision: APPROVE|REJECT, comment}`; requires
    `If-Match` with the incident version (optimistic concurrency).
- Errors as RFC 9457 Problem Details. OpenAPI generated and checked in tests.
- Authentication: OIDC bearer tokens (Keycloak Dev Services locally).
  Roles: `incident-viewer` (read), `incident-approver` (decide). The approver identity
  comes from the token, never from the request body.
- A decision is accepted only in `AWAITING_APPROVAL`; otherwise `409 Conflict`.
- Approval timeout (config) escalates the incident.

## Alternatives considered
- API keys: rejected — no user identity for audit.

## Consequences
- The future UI uses the same API and OIDC client.
