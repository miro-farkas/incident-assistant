import { describe, expect, it, vi } from "vitest";
import { browser, takeReturnPath } from "../auth/session";
import { json, problem, requestOf, stubFetch } from "../test/support";
import { ApiError, getCurrentUser, listIncidents } from "./client";

describe("API client", () => {
  it("sends the session cookie and never follows redirects", async () => {
    const fetchMock = stubFetch(json({ name: "Vera Viewer", roles: ["incident-viewer"] }));

    await expect(getCurrentUser()).resolves.toEqual({
      name: "Vera Viewer",
      roles: ["incident-viewer"],
    });
    const { url, init } = requestOf(fetchMock, 0);
    expect(url).toBe("/api/v1/me");
    expect(init).toMatchObject({ credentials: "same-origin", redirect: "manual" });
  });

  it("starts the login on 401 and returns to the page afterwards", async () => {
    window.history.replaceState(null, "", "/incidents/42?tab=steps");
    const assign = vi.spyOn(browser, "assign").mockImplementation(() => undefined);
    stubFetch(problem(401, "A valid bearer token is required"));

    await expect(listIncidents(["TRIAGED"], 0, 20)).rejects.toMatchObject({ status: 401 });

    expect(assign).toHaveBeenCalledWith("/login");
    expect(takeReturnPath()).toBe("/incidents/42?tab=steps");
    expect(takeReturnPath()).toBe("/");
  });

  it("does not start the login again at the login callback", async () => {
    window.history.replaceState(null, "", "/login?code=abc&state=def");
    const assign = vi.spyOn(browser, "assign").mockImplementation(() => undefined);
    stubFetch(problem(401, "A valid bearer token is required"));

    await expect(getCurrentUser()).rejects.toBeInstanceOf(ApiError);

    expect(assign).not.toHaveBeenCalled();
  });

  it("passes the Problem Details text of other errors on", async () => {
    stubFetch(problem(400, "The request is invalid"));

    await expect(listIncidents([], 0, 20)).rejects.toMatchObject({
      status: 400,
      message: "The request is invalid",
    });
  });
});

describe("takeReturnPath", () => {
  it("returns only paths of this origin", () => {
    for (const saved of ["https://evil.example/", "//evil.example/x", "/\\evil.example", "/login"]) {
      window.sessionStorage.setItem("incident-ui.return-to", saved);
      expect(takeReturnPath()).toBe("/");
    }
  });
});
