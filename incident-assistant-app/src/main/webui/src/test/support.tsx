import { render } from "@testing-library/react";
import type { ReactElement } from "react";
import { MemoryRouter, Route, Routes } from "react-router";
import { vi } from "vitest";
import type { CurrentUser, IncidentDetail, IncidentState } from "../api/types";
import { CurrentUserContext } from "../auth/CurrentUser";
import type { LiveEvent } from "../live/events";
import { LiveEventsContext, type LiveEventListener, type LiveEvents } from "../live/LiveEvents";

export const INCIDENT_ID = "6f1c1a52-8f0e-4a43-9d4b-2c0f3b4f6a10";

export const VIEWER: CurrentUser = { name: "Vera Viewer", roles: ["incident-viewer"] };
export const APPROVER: CurrentUser = {
  name: "Arno Approver",
  roles: ["incident-approver", "incident-viewer"],
};

export function incident(overrides: Partial<IncidentDetail> = {}): IncidentDetail {
  return {
    id: INCIDENT_ID,
    source: "prometheus",
    fingerprint: "fp-checkout-errors",
    state: "AWAITING_APPROVAL",
    version: 3,
    createdAt: "2026-10-18T10:00:00Z",
    updatedAt: "2026-10-18T10:05:00Z",
    alert: {
      source: "prometheus",
      fingerprint: "fp-checkout-errors",
      alertName: "HighErrorRate",
      summary: "Error rate above 5%",
      service: "checkout",
      severity: "critical",
      startsAt: "2026-10-18T09:58:00Z",
      labels: { team: "payments" },
    } as unknown as IncidentDetail["alert"],
    occurrenceCount: 2,
    steps: [
      {
        id: "0d3c6f1e-0000-4000-8000-000000000001",
        type: "TRIAGE",
        status: "SUCCEEDED",
        fromState: "RECEIVED",
        toState: "TRIAGED",
        startedAt: "2026-10-18T10:01:00Z",
        finishedAt: "2026-10-18T10:01:02Z",
        durationMs: 2000,
        output: { severity: "HIGH", category: "errors" } as unknown as Record<string, never>,
      },
    ],
    ...overrides,
  };
}

export function withState(state: IncidentState, version: number): IncidentDetail {
  return incident({ state, version });
}

/** A connected stream the test drives: `emit` delivers an event to the subscribed screens. */
export class FakeLiveEvents {
  private readonly listeners = new Set<LiveEventListener>();

  readonly value: LiveEvents = {
    status: "live",
    connection: 1,
    subscribe: (listener) => {
      this.listeners.add(listener);
      return () => this.listeners.delete(listener);
    },
  };

  emit(event: LiveEvent): void {
    this.listeners.forEach((listener) => listener(event));
  }
}

export interface Rendered {
  live: FakeLiveEvents;
}

/** Renders `element` at `path` (matched by `route`) for `user` with a fake event stream. */
export function renderAt(
  element: ReactElement,
  { route, path, user }: { route: string; path: string; user: CurrentUser },
): Rendered {
  const live = new FakeLiveEvents();
  render(
    <CurrentUserContext.Provider value={user}>
      <LiveEventsContext.Provider value={live.value}>
        <MemoryRouter initialEntries={[path]}>
          <Routes>
            <Route path={route} element={element} />
          </Routes>
        </MemoryRouter>
      </LiveEventsContext.Provider>
    </CurrentUserContext.Provider>,
  );
  return { live };
}

export function json(body: unknown, init: ResponseInit = {}): Response {
  return new Response(JSON.stringify(body), {
    status: 200,
    headers: { "Content-Type": "application/json" },
    ...init,
  });
}

export function problem(status: number, detail: string): Response {
  return json({ type: "about:blank", status, detail }, {
    status,
    headers: { "Content-Type": "application/problem+json" },
  });
}

/** Replaces `fetch`; each call takes the next answer. */
export function stubFetch(...answers: Response[]) {
  const fetchMock = vi.fn<typeof fetch>();
  answers.forEach((answer) => fetchMock.mockResolvedValueOnce(answer));
  vi.stubGlobal("fetch", fetchMock);
  return fetchMock;
}

export function requestOf(fetchMock: ReturnType<typeof stubFetch>, call: number) {
  const [url, init] = fetchMock.mock.calls[call] ?? [];
  return { url: String(url), init: init ?? {} };
}
