import { startLogin } from "../auth/session";
import type {
  CurrentUser,
  DecisionRequest,
  IncidentDetail,
  IncidentPage,
  IncidentState,
} from "./types";

const API = "/api/v1";

/** CSRF protection (P-0019): the service rejects state-changing session requests without it. */
export const CSRF_HEADER = "X-Requested-By";
export const CSRF_VALUE = "incident-ui";

/** An error response of the API; `detail` is the Problem Details text, if any. */
export class ApiError extends Error {
  readonly status: number;
  readonly detail: string | undefined;

  constructor(status: number, detail?: string) {
    super(detail ?? `Request failed with status ${status}`);
    this.name = "ApiError";
    this.status = status;
    this.detail = detail;
  }
}

/**
 * Sends a request with the session cookie. A missing or expired session (401, or a redirect the
 * service would only send to start a login) starts the login and fails the request.
 */
async function send(path: string, init: RequestInit = {}): Promise<Response> {
  const response = await fetch(API + path, {
    ...init,
    credentials: "same-origin",
    redirect: "manual",
    headers: { Accept: "application/json", ...init.headers },
  });
  if (response.status === 401 || response.type === "opaqueredirect") {
    startLogin();
    throw new ApiError(401, "Your session has ended. Sign in again.");
  }
  if (!response.ok) {
    throw new ApiError(response.status, await problemDetail(response));
  }
  return response;
}

async function problemDetail(response: Response): Promise<string | undefined> {
  try {
    const body: unknown = await response.json();
    if (typeof body === "object" && body !== null && "detail" in body) {
      return typeof body.detail === "string" ? body.detail : undefined;
    }
  } catch {
    // Not a Problem Details body.
  }
  return undefined;
}

export async function getCurrentUser(): Promise<CurrentUser> {
  return (await send("/me")).json();
}

export async function listIncidents(
  states: readonly IncidentState[],
  page: number,
  size: number,
): Promise<IncidentPage> {
  const query = new URLSearchParams();
  states.forEach((state) => query.append("state", state));
  query.set("page", String(page));
  query.set("size", String(size));
  return (await send(`/incidents?${query.toString()}`)).json();
}

export async function getIncident(id: string): Promise<IncidentDetail> {
  return (await send(`/incidents/${encodeURIComponent(id)}`)).json();
}

/** Decides on the pending proposal of the incident at `version` (If-Match, A-0006). */
export async function decide(
  id: string,
  version: number,
  decision: DecisionRequest,
): Promise<IncidentDetail> {
  const response = await send(`/incidents/${encodeURIComponent(id)}/decision`, {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      "If-Match": `"${version}"`,
      [CSRF_HEADER]: CSRF_VALUE,
    },
    body: JSON.stringify(decision),
  });
  return response.json();
}
