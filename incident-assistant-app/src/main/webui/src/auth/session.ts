// Browser login (A-0010): the service runs the code flow at /login and keeps the tokens; the UI
// only remembers where the user wanted to go.

export const LOGIN_PATH = "/login";
export const LOGOUT_PATH = "/logout";

const RETURN_TO_KEY = "incident-ui.return-to";

/** Full page navigation, replaceable in tests (jsdom does not navigate). */
export const browser = {
  assign(path: string): void {
    window.location.assign(path);
  },
};

/**
 * Sends the browser to the login of the service. At /login itself the callback has just failed to
 * produce a session; starting again would loop, so the caller shows an error instead.
 */
export function startLogin(): void {
  const { pathname, search } = window.location;
  if (pathname === LOGIN_PATH) {
    return;
  }
  try {
    window.sessionStorage.setItem(RETURN_TO_KEY, pathname + search);
  } catch {
    // Storage blocked: the user lands on the start page after login.
  }
  browser.assign(LOGIN_PATH);
}

export function isLoginCallback(): boolean {
  return window.location.pathname === LOGIN_PATH;
}

/** The path saved by `startLogin`, once; only paths of this UI, never the login itself. */
export function takeReturnPath(): string {
  let saved: string | null = null;
  try {
    saved = window.sessionStorage.getItem(RETURN_TO_KEY);
    window.sessionStorage.removeItem(RETURN_TO_KEY);
  } catch {
    // Storage blocked: fall back to the start page.
  }
  if (saved === null) {
    return "/";
  }
  const target = new URL(saved, window.location.origin);
  if (target.origin !== window.location.origin || target.pathname === LOGIN_PATH) {
    return "/";
  }
  return target.pathname + target.search;
}
