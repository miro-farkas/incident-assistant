// Events of GET /api/v1/incidents/stream (A-0009). They are hints (P-0018): the UI reloads what it
// shows from the REST API, except the occurrences count, which it takes from the event (A-0012).
// Only the fields the UI uses are read; schemas in docs/schemas/incident-*-event-v1.json.

export const STATE_CHANGED = "incident.state-changed";
export const OCCURRENCE_RECORDED = "incident.occurrence-recorded";
export const EVENT_TYPES = [STATE_CHANGED, OCCURRENCE_RECORDED] as const;

export interface StateChangedEvent {
  eventType: typeof STATE_CHANGED;
  incidentId: string;
  incidentVersion: number;
}

export interface OccurrenceRecordedEvent {
  eventType: typeof OCCURRENCE_RECORDED;
  incidentId: string;
  occurrenceCount: number;
}

export type LiveEvent = StateChangedEvent | OccurrenceRecordedEvent;

/** The event in an SSE message, or `null` if the message is not one the UI understands. */
export function parseLiveEvent(data: string): LiveEvent | null {
  let value: unknown;
  try {
    value = JSON.parse(data);
  } catch {
    return null;
  }
  if (typeof value !== "object" || value === null) {
    return null;
  }
  const event = value as Record<string, unknown>;
  if (typeof event.incidentId !== "string") {
    return null;
  }
  if (event.eventType === STATE_CHANGED && typeof event.incidentVersion === "number") {
    return {
      eventType: STATE_CHANGED,
      incidentId: event.incidentId,
      incidentVersion: event.incidentVersion,
    };
  }
  if (event.eventType === OCCURRENCE_RECORDED && typeof event.occurrenceCount === "number") {
    return {
      eventType: OCCURRENCE_RECORDED,
      incidentId: event.incidentId,
      occurrenceCount: event.occurrenceCount,
    };
  }
  return null;
}
