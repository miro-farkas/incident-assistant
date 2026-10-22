import type { IncidentState } from "../api/types";
import { stateLabel } from "../format";

export function StateBadge({ state }: { state: IncidentState | undefined }) {
  const modifier = state === undefined ? "unknown" : state.toLowerCase().replaceAll("_", "-");
  return <span className={`state state-${modifier}`}>{stateLabel(state)}</span>;
}
