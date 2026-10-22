import { useState } from "react";
import { ApiError, decide } from "../api/client";
import { MAX_COMMENT_LENGTH, type Decision, type IncidentDetail } from "../api/types";

/** What the detail screen does after a refused decision (A-0010). */
export type DecisionConflict = "changed" | "not-awaiting";

interface Props {
  incident: IncidentDetail;
  onDecided(incident: IncidentDetail): void;
  onConflict(conflict: DecisionConflict): void;
}

/** Approve or reject, sent with the version on screen as `If-Match` (A-0006). */
export function DecisionPanel({ incident, onDecided, onConflict }: Props) {
  const [comment, setComment] = useState("");
  const [sending, setSending] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const submit = async (decision: Decision) => {
    if (incident.id === undefined || incident.version === undefined) {
      return;
    }
    setSending(true);
    setError(null);
    try {
      const trimmed = comment.trim();
      onDecided(
        await decide(incident.id, incident.version, {
          decision,
          ...(trimmed === "" ? {} : { comment: trimmed }),
        }),
      );
    } catch (e: unknown) {
      if (e instanceof ApiError && e.status === 412) {
        onConflict("changed");
      } else if (e instanceof ApiError && e.status === 409) {
        onConflict("not-awaiting");
      } else {
        setError(e instanceof ApiError ? e.message : "The decision could not be sent.");
      }
    } finally {
      setSending(false);
    }
  };

  return (
    <section className="decision-panel" aria-labelledby="decide-heading">
      <h2 id="decide-heading">Decision</h2>
      <label htmlFor="decision-comment">Comment (optional)</label>
      <textarea
        id="decision-comment"
        value={comment}
        maxLength={MAX_COMMENT_LENGTH}
        rows={3}
        onChange={(event) => setComment(event.target.value)}
      />
      {error !== null && (
        <p role="alert" className="error">
          {error}
        </p>
      )}
      <div className="decision-buttons">
        <button
          type="button"
          className="approve"
          disabled={sending}
          onClick={() => void submit("APPROVE")}
        >
          Approve
        </button>
        <button
          type="button"
          className="reject"
          disabled={sending}
          onClick={() => void submit("REJECT")}
        >
          Reject
        </button>
      </div>
    </section>
  );
}
