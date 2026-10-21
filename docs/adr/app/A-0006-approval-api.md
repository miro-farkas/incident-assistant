# A-0006: Human approval REST API

- Status: Accepted
- Date: 2026-10-18
- Applies: P-0009

## Context
A human must approve or reject the proposed remediation. The UI comes later; the API must
already be safe to expose.

## Decision
- Endpoints under `/api/v1/incidents`:
  - `GET /` — list, newest first, filter by state (`state`, repeatable; all states when
    absent), paging (`page` from 0, `size` 1–100, default 20). Response
    `{items, page, size, totalItems, totalPages}`; an unknown state is `400`.
  - `GET /{id}` — incident detail: alert as received, state, version, occurrences count,
    step history with each step's output as stored JSON, and the decision (or `null`).
    `ETag` is the version in quotes, for example `"3"`.
  - `POST /{id}/decision` — body `{decision: APPROVE|REJECT, comment}` (comment optional,
    at most 2000 characters); requires `If-Match` with the incident's `ETag`. Missing (or
    `*`) → `428 Precondition Required`, malformed or weak → `400`, stale → `412`. Returns
    the incident after the decision with its new `ETag`.
- Roles: `incident-viewer` (read), `incident-approver` (decide, and read). The approver
  identity stored in `approval_decision` is the token subject (`sub`).
- A decision is accepted only in `AWAITING_APPROVAL`; otherwise `409 Conflict`. It refers to
  the incident's latest proposal step. Approval moves the incident to `APPROVED`,
  rejection to `REJECTED` with reason code `REJECTED_BY_APPROVER`. A decision is not a
  workflow step: its event has `step` and `stepId` set to `null`.
- Concurrent decisions on one incident: the first wins, the others get `412`.
- Every error, including `401` (with `WWW-Authenticate: Bearer`) and `403`, is a Problem
  Details body; details never echo request content. Authentication is therefore not
  proactive: it runs when a secured endpoint is reached.
- The approval timeout (config) escalates the incident.

## Alternatives considered
- See P-0009. A separate step type `APPROVAL`: the decision has its own table already, and a
  step row would duplicate it without adding information.

## Consequences
- The future approval UI uses the same API and OIDC client.
- Typed step results (triage result, diagnosis, proposal) are added to the detail by the
  items that define them; until then the outputs are returned as stored.
