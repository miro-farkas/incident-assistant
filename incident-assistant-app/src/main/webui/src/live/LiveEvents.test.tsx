import { act, render, screen } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { json, stubFetch } from "../test/support";
import { OCCURRENCE_RECORDED } from "./events";
import { LiveEventsProvider, STREAM_URL, useLiveEvent, useLiveEvents } from "./LiveEvents";

/** Minimal EventSource; the test opens, fails and feeds it. */
class FakeEventSource {
  static readonly CONNECTING = 0;
  static readonly OPEN = 1;
  static readonly CLOSED = 2;
  static instances: FakeEventSource[] = [];

  readonly url: string;
  readyState = FakeEventSource.CONNECTING;
  onopen: (() => void) | null = null;
  onerror: (() => void) | null = null;
  private readonly listeners = new Map<string, (message: MessageEvent<string>) => void>();

  constructor(url: string) {
    this.url = url;
    FakeEventSource.instances.push(this);
  }

  addEventListener(type: string, listener: (message: MessageEvent<string>) => void) {
    this.listeners.set(type, listener);
  }

  close() {
    this.readyState = FakeEventSource.CLOSED;
  }

  open() {
    this.readyState = FakeEventSource.OPEN;
    this.onopen?.();
  }

  fail() {
    this.readyState = FakeEventSource.CLOSED;
    this.onerror?.();
  }

  send(type: string, data: unknown) {
    this.listeners.get(type)?.(new MessageEvent(type, { data: JSON.stringify(data) }));
  }
}

function Probe() {
  const { status, connection } = useLiveEvents();
  useLiveEvent((event) => {
    if (event.eventType === OCCURRENCE_RECORDED) {
      document.title = `count ${event.occurrenceCount}`;
    }
  });
  return (
    <p>
      {status} {connection}
    </p>
  );
}

describe("LiveEventsProvider", () => {
  beforeEach(() => {
    FakeEventSource.instances = [];
    vi.stubGlobal("EventSource", FakeEventSource);
  });
  afterEach(() => {
    vi.useRealTimers();
  });

  it("delivers stream events and counts connections", () => {
    render(
      <LiveEventsProvider>
        <Probe />
      </LiveEventsProvider>,
    );
    const source = FakeEventSource.instances[0]!;
    expect(source.url).toBe(STREAM_URL);
    expect(screen.getByText("connecting 0")).toBeInTheDocument();

    act(() => source.open());
    expect(screen.getByText("live 1")).toBeInTheDocument();

    act(() =>
      source.send(OCCURRENCE_RECORDED, {
        eventType: OCCURRENCE_RECORDED,
        incidentId: "i-1",
        occurrenceCount: 7,
      }),
    );
    expect(document.title).toBe("count 7");
  });

  it("checks the session and opens a new stream after the service ended one", async () => {
    vi.useFakeTimers();
    const fetchMock = stubFetch(json({ name: "Vera Viewer", roles: ["incident-viewer"] }));
    render(
      <LiveEventsProvider>
        <Probe />
      </LiveEventsProvider>,
    );
    act(() => FakeEventSource.instances[0]!.open());

    await act(async () => FakeEventSource.instances[0]!.fail());
    expect(screen.getByText("offline 1")).toBeInTheDocument();
    expect(fetchMock).toHaveBeenCalledTimes(1);

    await act(async () => vi.advanceTimersByTimeAsync(5000));
    expect(FakeEventSource.instances).toHaveLength(2);
    act(() => FakeEventSource.instances[1]!.open());
    expect(screen.getByText("live 2")).toBeInTheDocument();
  });
});
