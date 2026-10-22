import type { IncidentState } from "./api/types";

const NONE = "–";

/** A timestamp of the API in the user's locale and time zone. */
export function formatTimestamp(value: string | null | undefined): string {
  if (value === null || value === undefined) {
    return NONE;
  }
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? value : date.toLocaleString();
}

/** `AWAITING_APPROVAL` → `Awaiting approval`. */
export function stateLabel(state: IncidentState | null | undefined): string {
  if (state === null || state === undefined) {
    return NONE;
  }
  const words = state.toLowerCase().replaceAll("_", " ");
  return words.charAt(0).toUpperCase() + words.slice(1);
}

/** Any value as text; `null` and absent values as a dash. */
export function text(value: unknown): string {
  if (value === null || value === undefined || value === "") {
    return NONE;
  }
  return typeof value === "string" ? value : JSON.stringify(value);
}
