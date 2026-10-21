# A-0010: Incident UI

- Status: Proposed
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
    highlighted.
  - **Incident detail:** alert (name, source, service, severity, summary, labels), step
    history with timestamps and reason codes, occurrences count, approval decision if
    any. Updates live. Built in two stages, because the UI is implemented before the AI
    steps (backlog order):
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

## Alternatives considered
- See P-0019. A decision dialog without the evidence: approvers would decide blind, which
  defeats the human approval (A-0001).

## Consequences
- The detail endpoint (A-0006) must return everything the detail screen shows; gaps are
  added to the API, not worked around in the UI.
- Playwright (Quarkiverse `quarkus-playwright`) is added for one end-to-end test.
- Not in scope: knowledge base administration, editing incidents, user preferences.
