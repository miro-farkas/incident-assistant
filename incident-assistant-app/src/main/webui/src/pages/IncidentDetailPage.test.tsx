import { act, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it } from "vitest";
import { OCCURRENCE_RECORDED, STATE_CHANGED } from "../live/events";
import {
  APPROVER,
  INCIDENT_ID,
  VIEWER,
  incident,
  json,
  problem,
  renderAt,
  requestOf,
  stubFetch,
  withState,
} from "../test/support";
import { IncidentDetailPage } from "./IncidentDetailPage";

const DETAIL = `/api/v1/incidents/${INCIDENT_ID}`;

function renderDetail(user = APPROVER) {
  return renderAt(<IncidentDetailPage />, {
    route: "/incidents/:id",
    path: `/incidents/${INCIDENT_ID}`,
    user,
  });
}

describe("IncidentDetailPage", () => {
  it("shows the alert, the occurrences and each step output as key/value text", async () => {
    stubFetch(json(incident()));
    renderDetail(VIEWER);

    expect(await screen.findByRole("heading", { name: "HighErrorRate" })).toBeInTheDocument();
    expect(screen.getByText("Error rate above 5%")).toBeInTheDocument();
    expect(screen.getByText("checkout")).toBeInTheDocument();
    expect(screen.getByText("payments")).toBeInTheDocument();
    expect(screen.getByTestId("occurrence-count")).toHaveTextContent("2");
    expect(screen.getByText("TRIAGE · SUCCEEDED")).toBeInTheDocument();
    expect(screen.getByText("category")).toBeInTheDocument();
    expect(screen.getByText("errors")).toBeInTheDocument();
  });

  it("renders markup in alert fields as text", async () => {
    const hostile = '<img src="x" onerror="alert(1)">';
    stubFetch(
      json(
        incident({
          alert: { alertName: hostile, summary: hostile } as unknown as Record<string, never>,
        }),
      ),
    );
    renderDetail(VIEWER);

    expect(await screen.findByRole("heading", { name: hostile })).toBeInTheDocument();
    expect(document.querySelector("img")).toBeNull();
  });

  it("hides the decision from viewers", async () => {
    stubFetch(json(incident()));
    renderDetail(VIEWER);

    await screen.findByRole("heading", { name: "HighErrorRate" });
    expect(screen.queryByRole("button", { name: "Approve" })).not.toBeInTheDocument();
  });

  it("offers the decision to approvers only while awaiting approval", async () => {
    stubFetch(json(withState("TRIAGED", 1)));
    renderDetail(APPROVER);

    await screen.findByRole("heading", { name: "HighErrorRate" });
    expect(screen.queryByRole("button", { name: "Approve" })).not.toBeInTheDocument();
  });

  it("approves with the shown version as If-Match and the CSRF header", async () => {
    const fetchMock = stubFetch(
      json(incident()),
      json(
        incident({
          state: "APPROVED",
          version: 4,
          decision: {
            decision: "APPROVE",
            approverSubject: "approver-subject",
            comment: "Rollback is safe",
            decidedAt: "2026-10-18T10:06:00Z",
          },
        }),
      ),
    );
    renderDetail(APPROVER);

    await userEvent.type(await screen.findByLabelText("Comment (optional)"), "Rollback is safe");
    await userEvent.click(screen.getByRole("button", { name: "Approve" }));

    await waitFor(() => expect(screen.getByTestId("incident-state")).toHaveTextContent("Approved"));
    expect(screen.queryByRole("button", { name: "Approve" })).not.toBeInTheDocument();
    expect(screen.getByText("Rollback is safe")).toBeInTheDocument();
    const { url, init } = requestOf(fetchMock, 1);
    expect(url).toBe(`${DETAIL}/decision`);
    expect(init.method).toBe("POST");
    expect(init.headers).toMatchObject({ "If-Match": '"3"', "X-Requested-By": "incident-ui" });
    expect(JSON.parse(String(init.body))).toEqual({
      decision: "APPROVE",
      comment: "Rollback is safe",
    });
  });

  it("reloads and explains when the incident changed since it was shown (412)", async () => {
    const fetchMock = stubFetch(
      json(incident()),
      problem(412, "Incident is no longer at version 3"),
      json(incident({ version: 5 })),
    );
    renderDetail(APPROVER);

    await userEvent.click(await screen.findByRole("button", { name: "Reject" }));

    expect(await screen.findByRole("status")).toHaveTextContent("changed since it was shown");
    await waitFor(() => expect(fetchMock).toHaveBeenCalledTimes(3));
    expect(requestOf(fetchMock, 2).url).toBe(DETAIL);
  });

  it("explains that no decision is possible any more (409)", async () => {
    stubFetch(
      json(incident()),
      problem(409, "Incident is not awaiting approval"),
      json(withState("ESCALATED", 4)),
    );
    renderDetail(APPROVER);

    await userEvent.click(await screen.findByRole("button", { name: "Approve" }));

    expect(await screen.findByRole("status")).toHaveTextContent("no longer possible");
    await waitFor(() =>
      expect(screen.queryByRole("button", { name: "Approve" })).not.toBeInTheDocument(),
    );
  });

  it("keeps the highest occurrences count from live events", async () => {
    stubFetch(json(incident()));
    const { live } = renderDetail(VIEWER);
    await screen.findByRole("heading", { name: "HighErrorRate" });

    const occurrence = (occurrenceCount: number) =>
      act(() =>
        live.emit({ eventType: OCCURRENCE_RECORDED, incidentId: INCIDENT_ID, occurrenceCount }),
      );
    occurrence(4);
    occurrence(3);
    act(() =>
      live.emit({ eventType: OCCURRENCE_RECORDED, incidentId: "other", occurrenceCount: 9 }),
    );

    expect(screen.getByTestId("occurrence-count")).toHaveTextContent("4");
  });

  it("reloads on a state event newer than the shown version only", async () => {
    const fetchMock = stubFetch(json(incident()), json(withState("APPROVED", 4)));
    const { live } = renderDetail(VIEWER);
    await screen.findByRole("heading", { name: "HighErrorRate" });

    act(() => live.emit({ eventType: STATE_CHANGED, incidentId: INCIDENT_ID, incidentVersion: 3 }));
    expect(fetchMock).toHaveBeenCalledTimes(1);

    act(() => live.emit({ eventType: STATE_CHANGED, incidentId: INCIDENT_ID, incidentVersion: 4 }));
    await waitFor(() => expect(screen.getByTestId("incident-state")).toHaveTextContent("Approved"));
    expect(fetchMock).toHaveBeenCalledTimes(2);
  });

  it("says when the incident does not exist", async () => {
    stubFetch(problem(404, "Incident not found"));
    renderDetail(VIEWER);

    expect(await screen.findByRole("alert")).toHaveTextContent("does not exist");
  });
});
