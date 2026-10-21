# P-0019: Web UI served by the service

- Status: Accepted
- Date: 2026-10-21
- Amends: P-0009 (authentication of browser clients)

## Context
Humans need a UI on top of the REST API (P-0009). The UI is used by one team for one
service and only calls that service's API. Browser applications that hold OAuth access
tokens in JavaScript expose them to any cross-site scripting flaw; the current guidance
for browser-based apps is to keep tokens on the server and give the browser only a
session cookie. Workflow data shown in the UI contains untrusted content (inbound
payloads, LLM output).

## Decision
- The UI is a single-page application in React and TypeScript, built with Vite. Its
  source lives in the service module under `src/main/webui` and is built, tested and
  served by the service through Quarkus Quinoa (Quarkiverse). UI and API share one origin.
- The service acts as the UI's backend for authentication: Quarkus OIDC in hybrid mode.
  Browsers log in with the authorization code flow handled by the service and receive an
  encrypted, `HttpOnly`, `Secure`, `SameSite=Strict` session cookie; no token is visible to
  JavaScript. Machine clients keep using bearer tokens on the same endpoints (P-0009).
  Roles and the acting user's identity come from the token in both cases.
- State-changing requests authenticated by the session cookie must carry a custom request
  header that the UI sets and cross-site forms cannot; requests without it are rejected
  (`403`). Together with `SameSite=Strict` this is the CSRF protection.
- An endpoint `GET /api/v1/me` returns the user's name and roles, so the UI can hide
  actions the user may not take. The API still enforces every role.
- Untrusted content is rendered as text only: no HTML injection
  (`dangerouslySetInnerHTML`), no Markdown rendering, no links built from it. The service
  sends a strict Content Security Policy (no inline scripts, no third-party origins).
- API types are generated from the service's OpenAPI document, so a breaking API change
  fails the UI build.
- Quinoa installs a pinned Node.js and package manager for the build; the lock file is
  committed. Unit and component tests (Vitest, Testing Library) run in `./mvnw verify`.

## Alternatives considered
- Tokens in the browser (authorization code + PKCE in the SPA, bearer calls): simpler
  OIDC setup, but tokens are exposed to scripts, and Server-Sent Events (P-0018) cannot
  send an `Authorization` header with the standard browser API.
- Separate static hosting or a separate backend-for-frontend service: a second deployable
  and cross-origin setup for a UI that only one service uses.
- Server-rendered pages (Qute with htmx): no JavaScript toolchain, but a weaker fit for an
  interactive, live-updating UI, and fewer people know the stack.

## Consequences
- The UI is released with the service. If it ever needs its own release cycle, it can
  move to its own hosting with a backend-for-frontend; the API does not change.
- The OIDC client of the service becomes confidential (it holds a client secret to
  redeem the code), and logout must end both the session and the identity provider
  session.
- npm packages are dependencies outside the Quarkus platform BOM; adding one is stated
  and justified like any other dependency.
- Not in scope: offline use, internationalisation, a design system beyond basic styling.
