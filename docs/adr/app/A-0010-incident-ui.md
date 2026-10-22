# A-0010: Incident UI

- Status: Accepted
- Date: 2026-10-21
- Applies: P-0019, P-0018, P-0009

## Context
Approvers decide on remediation proposals through the approval API (A-0006). They need to
see why the workflow proposes an action: the alert, the triage result, the diagnosis with
its evidence and the proposal. Viewers follow incidents without deciding.

## Decision
- React + TypeScript SPA in `incident-assistant-app/src/main/webui`, served by Quinoa at
  `/` with SPA routing; the API stays under `/api/v1`.
- Screens:
  - **Incidents:** list filtered by state (default: open states), paging, live updates
    from `GET /api/v1/incidents/stream` (A-0009); an `AWAITING_APPROVAL` incident is
    highlighted. Each row shows the occurrences count, raised at once from
    `incident.occurrence-recorded` (highest value seen, A-0012) besides the page reload
    (amended by INC-018).
  - **Incident detail:** alert (name, source, service, severity, summary, labels), step
    history with timestamps and reason codes, occurrences count (updated live from
    `incident.occurrence-recorded`, A-0012), approval decision if any. Updates live.
    Built in two stages, because the UI is implemented before the AI steps (backlog
    order):
    - first (INC-015): each step's output shown generically as a readable key/value view;
    - then, with the item that defines each result: triage result (INC-006), diagnosis
      with hypothesis, evidence and source references, confidence (INC-007), remediation
      proposal with action, target, parameters, rationale, risk (INC-008).
  - **Decision:** approve or reject with an optional comment, only for
    `incident-approver` and only in `AWAITING_APPROVAL`. Sent with `If-Match` of the version
    shown. `412` reloads the incident and tells the user it changed; `409` shows that a
    decision is no longer possible.
- Authentication: Keycloak client `incident-assistant` (confidential, standard flow) used
  by the service in hybrid mode; login on first access, logout from the header. The local
  realm (P-0016) gets the client and two users, one per role.
- CSRF header: `X-Requested-By: incident-ui` on every state-changing request.
- `GET /api/v1/me` returns `{name, roles}` filtered to the `incident-*` roles.
- All alert fields and all LLM output are rendered as plain text (P-0019); evidence
  source references are shown as text, not links.

## Implementation (INC-015)
- Login: the UI itself is public static content; it calls `GET /api/v1/me` and, on `401`, saves
  the current location and navigates to `/login`, the only path (with `/logout`) that requires
  authentication at the HTTP layer. `/login` is also the fixed redirect URI. The code is redeemed
  there and the UI is served in the same response, without the usual redirect that removes `code`
  and `state`: that redirect would be part of a redirect chain started at the identity provider,
  so with a cross-site identity provider the browser would not send the `SameSite=Strict` session
  cookie and the login would start again. The UI then replaces the address with the saved
  location (same origin only); `Referrer-Policy: no-referrer` keeps the redeemed code out of
  `Referer` headers. API endpoints never redirect: without a session they answer `401`.
- REST is mounted at `/api` (`quarkus.rest.path`; resource paths start at `/v1`). It answers
  unknown paths below its root with a `404` Problem Details itself (the Problem Details mapper of
  P-0009 takes precedence over passing the request on), so it must not own `/`: every other path
  falls back to the UI's `index.html` (SPA routing).
- The CSRF check applies to `POST`/`PUT`/`PATCH`/`DELETE` requests whose identity comes from the
  code flow (it carries the ID token as credential); bearer-token requests are unaffected.
- The CSP header is set on every path except `/q/*` (dev tools with inline scripts); dev mode
  allows the inline scripts and websocket of the Vite dev server.
- API types: the service's OpenAPI document is checked in as a snapshot
  (`src/main/webui/openapi/incident-assistant.json`) that a test keeps equal to the generated
  document; the UI build generates its types from it (`openapi-typescript`) and type-checks.
- Live updates: one `EventSource` per browser tab. The list reloads its page on any event
  (debounced) and on every (re)connect; the detail reloads on a state event with a newer
  `incidentVersion` and on (re)connect, and takes the occurrences count from occurrence events
  (highest value seen). When the service ends or refuses the stream, the UI checks the session
  (a `401` starts the login) and reconnects after 5 s.
- npm dependencies: React, React DOM and React Router at runtime; Vite, TypeScript, Vitest,
  Testing Library, jsdom and openapi-typescript for build and tests. Node.js is pinned in
  `application.properties` and installed by Quinoa.
- The browser test (`IncidentUiEndToEndTest`, test package `dev.mfa.incident.webui`, apart from
  the API tests) runs against a Keycloak Dev Service with the checked-in realm and adds its random
  port to the client's redirect URIs through the admin API.

## Alternatives considered
- See P-0019. A decision dialog without the evidence: approvers would decide blind, which
  defeats the human approval (A-0001).

## Consequences
- The detail endpoint (A-0006) must return everything the detail screen shows; gaps are
  added to the API, not worked around in the UI.
- Playwright (Quarkiverse `quarkus-playwright`) is added for one end-to-end test.
- Not in scope: knowledge base administration, editing incidents, user preferences.
