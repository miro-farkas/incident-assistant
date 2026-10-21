# P-0009: REST API conventions and security

- Status: Accepted
- Date: 2026-10-18

## Context
Humans act on workflows through a REST API, first directly, later through a UI. The API
must be safe to expose from the first endpoint, and every human decision must be
attributable.

## Decision
- Resources under `/api/v1/<collection>`; list endpoints support filtering and paging.
- State-changing requests on an existing resource require `If-Match` with the resource
  version (optimistic concurrency): stale version → `412 Precondition Failed`; request not
  allowed in the resource's current state → `409 Conflict`.
- Errors are RFC 9457 Problem Details. OpenAPI is generated and checked in tests.
- Authentication with OIDC bearer tokens (Keycloak Dev Services locally). Authorisation by
  roles per operation (read vs. decide). The acting user's identity comes from the token,
  never from the request body, and is stored with the decision.
- Resources contain no business logic; they map between HTTP and service DTOs (P-0013).

## Alternatives considered
- API keys: no user identity for audit.

## Consequences
- A future UI uses the same API and OIDC client.
- Browser clients authenticate with a server-side session instead of bearer tokens
  (P-0019).
