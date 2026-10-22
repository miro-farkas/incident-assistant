import { act, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it } from "vitest";
import type { Incident, IncidentPage } from "../api/types";
import { STATE_CHANGED } from "../live/events";
import { VIEWER, json, renderAt, requestOf, stubFetch } from "../test/support";
import { IncidentListPage } from "./IncidentListPage";

const PENDING = "11111111-1111-4111-8111-111111111111";
const TRIAGED = "22222222-2222-4222-8222-222222222222";

function summary(id: string, state: Incident["state"], fingerprint: string): Incident {
  return {
    id,
    source: "prometheus",
    fingerprint,
    state,
    version: 1,
    createdAt: "2026-10-18T10:00:00Z",
    updatedAt: "2026-10-18T10:00:00Z",
  };
}

function page(items: Incident[], totalPages = 1): IncidentPage {
  return { items, page: 0, size: 20, totalItems: items.length, totalPages };
}

function renderList(path = "/") {
  return renderAt(<IncidentListPage />, { route: "/", path, user: VIEWER });
}

function stateParams(url: string): string[] {
  return new URL(url, "http://localhost").searchParams.getAll("state");
}

describe("IncidentListPage", () => {
  it("lists the open states by default and highlights incidents awaiting approval", async () => {
    const fetchMock = stubFetch(
      json(
        page([
          summary(PENDING, "AWAITING_APPROVAL", "fp-pending"),
          summary(TRIAGED, "TRIAGED", "fp-triaged"),
        ]),
      ),
    );
    renderList();

    const pending = await screen.findByTestId(`incident-${PENDING}`);
    expect(pending).toHaveClass("awaiting-approval");
    expect(screen.getByTestId(`incident-${TRIAGED}`)).not.toHaveClass("awaiting-approval");
    expect(within(pending).getByRole("link", { name: "fp-pending" })).toHaveAttribute(
      "href",
      `/incidents/${PENDING}`,
    );
    expect(stateParams(requestOf(fetchMock, 0).url)).toEqual([
      "RECEIVED",
      "TRIAGED",
      "DIAGNOSED",
      "AWAITING_APPROVAL",
      "APPROVED",
    ]);
  });

  it("filters by the selected states", async () => {
    const fetchMock = stubFetch(json(page([])), json(page([])));
    renderList();
    await screen.findByText("No incidents in the selected states.");

    await userEvent.click(screen.getByRole("checkbox", { name: "Escalated" }));

    await waitFor(() => expect(fetchMock).toHaveBeenCalledTimes(2));
    expect(stateParams(requestOf(fetchMock, 1).url)).toContain("ESCALATED");
  });

  it("reloads when an event arrives", async () => {
    const fetchMock = stubFetch(
      json(page([summary(PENDING, "AWAITING_APPROVAL", "fp-pending")])),
      json(page([summary(PENDING, "APPROVED", "fp-pending")])),
    );
    const { live } = renderList();
    await screen.findByTestId(`incident-${PENDING}`);

    act(() => live.emit({ eventType: STATE_CHANGED, incidentId: PENDING, incidentVersion: 2 }));

    await waitFor(() =>
      expect(screen.getByTestId(`incident-${PENDING}`)).not.toHaveClass("awaiting-approval"),
    );
    expect(fetchMock).toHaveBeenCalledTimes(2);
  });

  it("pages through the results", async () => {
    const fetchMock = stubFetch(
      json(page([summary(PENDING, "AWAITING_APPROVAL", "fp-pending")], 2)),
      json({ ...page([summary(TRIAGED, "TRIAGED", "fp-triaged")], 2), page: 1 }),
    );
    renderList();
    await screen.findByText("Page 1 of 2");

    await userEvent.click(screen.getByRole("button", { name: "Next" }));

    await screen.findByTestId(`incident-${TRIAGED}`);
    expect(new URL(requestOf(fetchMock, 1).url, "http://localhost").searchParams.get("page")).toBe(
      "1",
    );
  });
});
