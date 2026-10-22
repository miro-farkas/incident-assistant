import {
  createContext,
  useContext,
  useEffect,
  useMemo,
  useRef,
  useState,
  type ReactNode,
} from "react";
import { getCurrentUser } from "../api/client";
import { EVENT_TYPES, parseLiveEvent, type LiveEvent } from "./events";

export const STREAM_URL = "/api/v1/incidents/stream";

/** Wait before opening a new stream after the service closed or refused one (503, A-0009). */
const RECONNECT_DELAY_MS = 5000;

export type StreamStatus = "connecting" | "live" | "offline";

export type LiveEventListener = (event: LiveEvent) => void;

export interface LiveEvents {
  status: StreamStatus;
  /** Counts the opened connections; screens reload when it changes, as nothing is replayed. */
  connection: number;
  subscribe(listener: LiveEventListener): () => void;
}

export const LiveEventsContext = createContext<LiveEvents | null>(null);

export function useLiveEvents(): LiveEvents {
  const value = useContext(LiveEventsContext);
  if (value === null) {
    throw new Error("useLiveEvents needs a LiveEventsProvider");
  }
  return value;
}

/** Calls `listener` for every event while the component is mounted. */
export function useLiveEvent(listener: LiveEventListener): void {
  const { subscribe } = useLiveEvents();
  const latest = useRef(listener);
  latest.current = listener;
  useEffect(() => subscribe((event) => latest.current(event)), [subscribe]);
}

/**
 * One event stream for the whole UI. The browser reconnects by itself after a network error; when
 * the service ends or refuses the stream, the provider checks the session (a 401 starts the login)
 * and opens a new stream after a delay.
 */
export function LiveEventsProvider({ children }: { children: ReactNode }) {
  const [status, setStatus] = useState<StreamStatus>("connecting");
  const [connection, setConnection] = useState(0);
  const listeners = useRef(new Set<LiveEventListener>());

  useEffect(() => {
    let source: EventSource | null = null;
    let retry: ReturnType<typeof setTimeout> | undefined;
    let stopped = false;

    const dispatch = (message: MessageEvent<string>) => {
      const event = parseLiveEvent(message.data);
      if (event !== null) {
        listeners.current.forEach((listener) => listener(event));
      }
    };

    const connect = () => {
      setStatus("connecting");
      source = new EventSource(STREAM_URL);
      EVENT_TYPES.forEach((type) => source!.addEventListener(type, dispatch));
      source.onopen = () => {
        setStatus("live");
        setConnection((count) => count + 1);
      };
      source.onerror = () => {
        if (source?.readyState !== EventSource.CLOSED) {
          setStatus("connecting");
          return;
        }
        source.close();
        setStatus("offline");
        getCurrentUser()
          .catch(() => undefined)
          .finally(() => {
            if (!stopped) {
              retry = setTimeout(connect, RECONNECT_DELAY_MS);
            }
          });
      };
    };

    connect();
    return () => {
      stopped = true;
      clearTimeout(retry);
      source?.close();
    };
  }, []);

  const value = useMemo<LiveEvents>(
    () => ({
      status,
      connection,
      subscribe(listener) {
        listeners.current.add(listener);
        return () => listeners.current.delete(listener);
      },
    }),
    [status, connection],
  );

  return <LiveEventsContext.Provider value={value}>{children}</LiveEventsContext.Provider>;
}
