import { useCallback, useEffect, useRef, useState } from "react";
import { Link, useParams } from "react-router";
import { ApiError, getIncident } from "../api/client";
import type { ApprovalDecision, IncidentDetail, Step } from "../api/types";
import { isApprover, useCurrentUser } from "../auth/CurrentUser";
import { DecisionPanel, type DecisionConflict } from "../components/DecisionPanel";
import { JsonView } from "../components/JsonView";
import { StateBadge } from "../components/StateBadge";
import { formatTimestamp, stateLabel, text } from "../format";
import { STATE_CHANGED } from "../live/events";
import { useLiveEvent, useLiveEvents } from "../live/LiveEvents";

const CONFLICT_NOTICES: Record<DecisionConflict, string> = {
  changed:
    "The incident changed since it was shown and has been reloaded. Review it and decide again.",
  "not-awaiting":
    "A decision is no longer possible: the incident is not awaiting approval any more.",
};

export function IncidentDetailPage() {
  const { id = "" } = useParams();
  const user = useCurrentUser();
  const { connection } = useLiveEvents();
  const [incident, setIncident] = useState<IncidentDetail | null>(null);
  const [occurrences, setOccurrences] = useState(0);
  const [error, setError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);
  const shownVersion = useRef(-1);
  const latestRequest = useRef(0);

  const show = useCallback((loaded: IncidentDetail) => {
    shownVersion.current = loaded.version ?? -1;
    setIncident(loaded);
    // A count from an event may be ahead of the detail that was read before it (A-0010).
    setOccurrences((count) => Math.max(count, loaded.occurrenceCount ?? 0));
    setError(null);
  }, []);

  const load = useCallback(() => {
    const request = ++latestRequest.current;
    getIncident(id)
      .then((loaded) => {
        if (request === latestRequest.current) {
          show(loaded);
        }
      })
      .catch((e: unknown) => {
        if (request === latestRequest.current) {
          setError(
            e instanceof ApiError && e.status === 404
              ? "This incident does not exist."
              : e instanceof ApiError
                ? e.message
                : "Loading failed.",
          );
        }
      });
  }, [id, show]);

  useEffect(() => {
    shownVersion.current = -1;
    setIncident(null);
    setOccurrences(0);
    setNotice(null);
  }, [id]);
  // Loads on open and on every (re)connect of the stream, which replays nothing.
  useEffect(() => load(), [load, connection]);

  useLiveEvent((event) => {
    if (event.incidentId !== id) {
      return;
    }
    if (event.eventType === STATE_CHANGED) {
      if (event.incidentVersion > shownVersion.current) {
        load();
      }
    } else {
      setOccurrences((count) => Math.max(count, event.occurrenceCount));
    }
  });

  const decided = (updated: IncidentDetail) => {
    // A state event of an older version may still be loading; the decision answer is newer.
    latestRequest.current++;
    setNotice(null);
    show(updated);
  };
  const conflict = (kind: DecisionConflict) => {
    setNotice(CONFLICT_NOTICES[kind]);
    load();
  };

  if (error !== null) {
    return (
      <section>
        <p role="alert" className="error">
          {error}
        </p>
        <Link to="/">Back to the incidents</Link>
      </section>
    );
  }
  if (incident === null) {
    return <p>Loading…</p>;
  }

  const canDecide = isApprover(user) && incident.state === "AWAITING_APPROVAL";
  return (
    <article aria-labelledby="incident-heading">
      <p>
        <Link to="/">← Incidents</Link>
      </p>
      <h1 id="incident-heading">{alertField(incident, "alertName")}</h1>
      <dl className="facts">
        <div>
          <dt>State</dt>
          <dd data-testid="incident-state">
            <StateBadge state={incident.state} />
          </dd>
        </div>
        <div>
          <dt>Version</dt>
          <dd>{incident.version}</dd>
        </div>
        <div>
          <dt>Occurrences</dt>
          <dd data-testid="occurrence-count">{occurrences}</dd>
        </div>
        <div>
          <dt>Created</dt>
          <dd>{formatTimestamp(incident.createdAt)}</dd>
        </div>
        <div>
          <dt>Updated</dt>
          <dd>{formatTimestamp(incident.updatedAt)}</dd>
        </div>
      </dl>

      {notice !== null && (
        <p role="status" className="notice">
          {notice}
        </p>
      )}

      <AlertSection incident={incident} />

      {incident.decision ? (
        <DecisionSection decision={incident.decision} />
      ) : (
        canDecide && <DecisionPanel incident={incident} onDecided={decided} onConflict={conflict} />
      )}

      <section aria-labelledby="steps-heading">
        <h2 id="steps-heading">Steps</h2>
        {(incident.steps ?? []).length === 0 ? (
          <p>No steps yet.</p>
        ) : (
          <ol className="steps">
            {(incident.steps ?? []).map((step) => (
              <StepItem key={step.id} step={step} />
            ))}
          </ol>
        )}
      </section>
    </article>
  );
}

/** The alert as received (alert-v1); its fields are untrusted and shown as text only. */
function alertOf(incident: IncidentDetail): Record<string, unknown> {
  const alert: unknown = incident.alert;
  return typeof alert === "object" && alert !== null ? (alert as Record<string, unknown>) : {};
}

function alertField(incident: IncidentDetail, field: string): string {
  return text(alertOf(incident)[field]);
}

function AlertSection({ incident }: { incident: IncidentDetail }) {
  const labels = alertOf(incident).labels;
  const fields: [string, string][] = [
    ["Name", "alertName"],
    ["Source", "source"],
    ["Service", "service"],
    ["Severity", "severity"],
    ["Summary", "summary"],
    ["Description", "description"],
    ["Started", "startsAt"],
    ["Source link", "generatorUrl"],
  ];
  return (
    <section aria-labelledby="alert-heading">
      <h2 id="alert-heading">Alert</h2>
      <dl className="facts">
        {fields.map(([label, field]) => (
          <div key={field}>
            <dt>{label}</dt>
            <dd>
              {field === "startsAt"
                ? formatTimestamp(alertField(incident, field))
                : alertField(incident, field)}
            </dd>
          </div>
        ))}
        <div>
          <dt>Labels</dt>
          <dd>
            {typeof labels === "object" && labels !== null ? <JsonView value={labels} /> : "–"}
          </dd>
        </div>
      </dl>
    </section>
  );
}

function DecisionSection({ decision }: { decision: ApprovalDecision }) {
  return (
    <section aria-labelledby="decision-heading">
      <h2 id="decision-heading">Decision</h2>
      <dl className="facts">
        <div>
          <dt>Decision</dt>
          <dd>{decision.decision === "APPROVE" ? "Approved" : "Rejected"}</dd>
        </div>
        <div>
          <dt>By</dt>
          <dd>{text(decision.approverSubject)}</dd>
        </div>
        <div>
          <dt>At</dt>
          <dd>{formatTimestamp(decision.decidedAt)}</dd>
        </div>
        <div>
          <dt>Comment</dt>
          <dd>{text(decision.comment)}</dd>
        </div>
      </dl>
    </section>
  );
}

function StepItem({ step }: { step: Step }) {
  return (
    <li className="step">
      <h3>
        {text(step.type)} · {text(step.status)}
      </h3>
      <dl className="facts">
        <div>
          <dt>Transition</dt>
          <dd>
            {stateLabel(step.fromState)} → {stateLabel(step.toState)}
          </dd>
        </div>
        <div>
          <dt>Started</dt>
          <dd>{formatTimestamp(step.startedAt)}</dd>
        </div>
        <div>
          <dt>Finished</dt>
          <dd>{formatTimestamp(step.finishedAt)}</dd>
        </div>
        <div>
          <dt>Reason</dt>
          <dd>{text(step.reasonCode)}</dd>
        </div>
        {step.errorMessage && (
          <div>
            <dt>Error</dt>
            <dd>{step.errorMessage}</dd>
          </div>
        )}
        {step.modelId && (
          <div>
            <dt>Model</dt>
            <dd>
              {step.modelId} · prompt {text(step.promptId)} {text(step.promptVersion)}
            </dd>
          </div>
        )}
        <div>
          <dt>Output</dt>
          <dd>
            <JsonView value={step.output} />
          </dd>
        </div>
      </dl>
    </li>
  );
}
