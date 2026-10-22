import { useCallback, useEffect, useRef, useState } from "react";
import { Link, useSearchParams } from "react-router";
import { ApiError, listIncidents } from "../api/client";
import {
  INCIDENT_STATES,
  OPEN_STATES,
  type IncidentPage,
  type IncidentState,
} from "../api/types";
import { StateBadge } from "../components/StateBadge";
import { formatTimestamp, stateLabel } from "../format";
import { useLiveEvent, useLiveEvents } from "../live/LiveEvents";

export const PAGE_SIZE = 20;

/** Events often come in bursts (one per step); one reload covers a burst. */
const RELOAD_DELAY_MS = 250;

/** States and page live in the address, so a filtered list can be bookmarked and shared. */
function readFilter(params: URLSearchParams): { states: IncidentState[]; page: number } {
  const selected = params.getAll("state");
  const states =
    selected.length === 0
      ? [...OPEN_STATES]
      : INCIDENT_STATES.filter((state) => selected.includes(state));
  const page = Number.parseInt(params.get("page") ?? "0", 10);
  return { states, page: Number.isInteger(page) && page >= 0 ? page : 0 };
}

export function IncidentListPage() {
  const [params, setParams] = useSearchParams();
  const { states, page } = readFilter(params);
  const statesKey = states.join(",");
  const { connection } = useLiveEvents();
  const [result, setResult] = useState<IncidentPage | null>(null);
  const [error, setError] = useState<string | null>(null);
  const reloadTimer = useRef<ReturnType<typeof setTimeout> | undefined>(undefined);
  const latestRequest = useRef(0);

  // Only the answer to the latest request is shown, whatever order the answers arrive in.
  const load = useCallback(() => {
    const request = ++latestRequest.current;
    listIncidents(statesKey.split(",") as IncidentState[], page, PAGE_SIZE)
      .then((loaded) => {
        if (request === latestRequest.current) {
          setResult(loaded);
          setError(null);
        }
      })
      .catch((e: unknown) => {
        if (request === latestRequest.current) {
          setError(e instanceof ApiError ? e.message : "Loading failed.");
        }
      });
  }, [statesKey, page]);

  // Loads on every filter change and on every (re)connect of the stream, which replays nothing.
  useEffect(() => load(), [load, connection]);
  useEffect(() => () => clearTimeout(reloadTimer.current), []);

  useLiveEvent(() => {
    clearTimeout(reloadTimer.current);
    reloadTimer.current = setTimeout(load, RELOAD_DELAY_MS);
  });

  const showStates = (next: readonly IncidentState[]) => {
    const query = new URLSearchParams();
    next.forEach((state) => query.append("state", state));
    setParams(query);
  };
  const toggle = (state: IncidentState) => {
    const next = states.includes(state)
      ? states.filter((s) => s !== state)
      : INCIDENT_STATES.filter((s) => s === state || states.includes(s));
    showStates(next.length === 0 ? OPEN_STATES : next);
  };
  const showPage = (next: number) => {
    const query = new URLSearchParams(params);
    query.set("page", String(next));
    setParams(query);
  };

  const totalPages = result?.totalPages ?? 0;
  return (
    <section aria-labelledby="incidents-heading">
      <h1 id="incidents-heading">Incidents</h1>
      <fieldset className="filter">
        <legend>States</legend>
        {INCIDENT_STATES.map((state) => (
          <label key={state}>
            <input
              type="checkbox"
              checked={states.includes(state)}
              onChange={() => toggle(state)}
            />
            {stateLabel(state)}
          </label>
        ))}
        <span className="filter-presets">
          <button type="button" onClick={() => showStates(OPEN_STATES)}>
            Open
          </button>
          <button type="button" onClick={() => showStates(INCIDENT_STATES)}>
            All
          </button>
        </span>
      </fieldset>

      {error !== null && (
        <p role="alert" className="error">
          {error}
        </p>
      )}
      {result === null && error === null && <p>Loading…</p>}
      {result !== null && (result.items ?? []).length === 0 && (
        <p>No incidents in the selected states.</p>
      )}
      {result !== null && (result.items ?? []).length > 0 && (
        <table className="incidents">
          <thead>
            <tr>
              <th scope="col">State</th>
              <th scope="col">Source</th>
              <th scope="col">Fingerprint</th>
              <th scope="col">Created</th>
              <th scope="col">Updated</th>
            </tr>
          </thead>
          <tbody>
            {(result.items ?? []).map((incident) => (
              <tr
                key={incident.id}
                className={incident.state === "AWAITING_APPROVAL" ? "awaiting-approval" : undefined}
                data-testid={`incident-${incident.id}`}
              >
                <td>
                  <StateBadge state={incident.state} />
                </td>
                <td>{incident.source}</td>
                <td>
                  <Link to={`/incidents/${incident.id}`}>{incident.fingerprint}</Link>
                </td>
                <td>{formatTimestamp(incident.createdAt)}</td>
                <td>{formatTimestamp(incident.updatedAt)}</td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
      {totalPages > 1 && (
        <nav className="paging" aria-label="Pages">
          <button type="button" disabled={page === 0} onClick={() => showPage(page - 1)}>
            Previous
          </button>
          <span>
            Page {page + 1} of {totalPages}
          </span>
          <button
            type="button"
            disabled={page + 1 >= totalPages}
            onClick={() => showPage(page + 1)}
          >
            Next
          </button>
        </nav>
      )}
    </section>
  );
}
