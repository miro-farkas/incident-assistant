import { describe, expect, it } from "vitest";
import { OCCURRENCE_RECORDED, STATE_CHANGED, parseLiveEvent } from "./events";

const INCIDENT = "6f1c1a52-8f0e-4a43-9d4b-2c0f3b4f6a10";

describe("parseLiveEvent", () => {
  it("reads state and occurrence events", () => {
    expect(
      parseLiveEvent(
        JSON.stringify({
          schemaVersion: 1,
          eventType: STATE_CHANGED,
          incidentId: INCIDENT,
          incidentVersion: 4,
          state: "APPROVED",
        }),
      ),
    ).toEqual({ eventType: STATE_CHANGED, incidentId: INCIDENT, incidentVersion: 4 });
    expect(
      parseLiveEvent(
        JSON.stringify({ eventType: OCCURRENCE_RECORDED, incidentId: INCIDENT, occurrenceCount: 2 }),
      ),
    ).toEqual({ eventType: OCCURRENCE_RECORDED, incidentId: INCIDENT, occurrenceCount: 2 });
  });

  it("ignores messages it does not understand", () => {
    expect(parseLiveEvent("not json")).toBeNull();
    expect(parseLiveEvent("null")).toBeNull();
    expect(parseLiveEvent(JSON.stringify({ eventType: "incident.deleted", incidentId: INCIDENT })))
      .toBeNull();
    expect(
      parseLiveEvent(JSON.stringify({ eventType: STATE_CHANGED, incidentId: INCIDENT })),
    ).toBeNull();
  });
});
