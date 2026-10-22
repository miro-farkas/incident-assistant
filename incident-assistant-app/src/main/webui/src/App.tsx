import { useEffect, useState } from "react";
import { Link, Route, Routes, useNavigate } from "react-router";
import { ApiError, getCurrentUser } from "./api/client";
import type { CurrentUser } from "./api/types";
import { CurrentUserContext } from "./auth/CurrentUser";
import { LOGIN_PATH, LOGOUT_PATH, isLoginCallback, takeReturnPath } from "./auth/session";
import { LiveEventsProvider, useLiveEvents, type StreamStatus } from "./live/LiveEvents";
import { IncidentDetailPage } from "./pages/IncidentDetailPage";
import { IncidentListPage } from "./pages/IncidentListPage";

type Session =
  | { kind: "loading" }
  | { kind: "signed-in"; user: CurrentUser }
  | { kind: "login-failed" }
  | { kind: "error"; message: string };

/**
 * Asks the service who is signed in. Without a session the request starts the login (client.ts);
 * back at /login after it, the UI returns to the page the user wanted and removes code and state
 * of the callback from the address.
 */
export function App() {
  const navigate = useNavigate();
  const [session, setSession] = useState<Session>({ kind: "loading" });

  useEffect(() => {
    getCurrentUser()
      .then((user) => {
        if (isLoginCallback()) {
          navigate(takeReturnPath(), { replace: true });
        }
        setSession({ kind: "signed-in", user });
      })
      .catch((e: unknown) => {
        if (e instanceof ApiError && e.status === 401) {
          setSession(isLoginCallback() ? { kind: "login-failed" } : { kind: "loading" });
        } else {
          setSession({ kind: "error", message: "The service is not reachable. Reload the page." });
        }
      });
  }, [navigate]);

  switch (session.kind) {
    case "loading":
      return <p className="splash">Signing in…</p>;
    case "login-failed":
      return (
        <main className="splash">
          <p role="alert">The sign-in did not complete.</p>
          <a href={LOGIN_PATH}>Sign in again</a>
        </main>
      );
    case "error":
      return (
        <main className="splash">
          <p role="alert">{session.message}</p>
        </main>
      );
    case "signed-in":
      return (
        <CurrentUserContext.Provider value={session.user}>
          {(session.user.roles ?? []).length === 0 ? (
            <>
              <Header user={session.user} />
              <main>
                <p role="alert">Your account has no incident role. Ask an administrator.</p>
              </main>
            </>
          ) : (
            <LiveEventsProvider>
              <Header user={session.user} live />
              <main>
                <Routes>
                  <Route path="/" element={<IncidentListPage />} />
                  <Route path="/incidents/:id" element={<IncidentDetailPage />} />
                  <Route path="*" element={<NotFound />} />
                </Routes>
              </main>
            </LiveEventsProvider>
          )}
        </CurrentUserContext.Provider>
      );
  }
}

function Header({ user, live = false }: { user: CurrentUser; live?: boolean }) {
  return (
    <header className="app-header">
      <Link to="/" className="app-title">
        Incident Assistant
      </Link>
      {live && <LiveStatus />}
      <span className="user">
        {user.name}
        {(user.roles ?? []).length > 0 && (
          <span className="roles"> ({(user.roles ?? []).join(", ")})</span>
        )}
      </span>
      <a href={LOGOUT_PATH}>Sign out</a>
    </header>
  );
}

const STATUS_LABELS: Record<StreamStatus, string> = {
  connecting: "Connecting…",
  live: "Live",
  offline: "Offline, retrying",
};

function LiveStatus() {
  const { status } = useLiveEvents();
  return (
    <span className={`live live-${status}`} data-testid="live-status">
      {STATUS_LABELS[status]}
    </span>
  );
}

function NotFound() {
  return (
    <section>
      <p>This page does not exist.</p>
      <Link to="/">Back to the incidents</Link>
    </section>
  );
}
