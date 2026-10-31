// Names for the generated API types (schema.d.ts, from openapi/incident-assistant.json). A change of
// the API that breaks the UI fails the type check of the build.
import type { components } from "./schema";

type Schemas = components["schemas"];

export type IncidentState = Schemas["IncidentState"];
export type Incident = Schemas["IncidentSummaryDto"];
export type IncidentPage = Schemas["IncidentPageDto"];
export type IncidentDetail = Schemas["IncidentDetailDto"];
export type Step = Schemas["StepDto"];
export type ApprovalDecision = Schemas["ApprovalDecisionDto"];
export type DecisionRequest = Schemas["DecisionDto"];
export type Decision = Schemas["Decision"];
export type CurrentUser = Schemas["CurrentUserDto"];
export type TriageResult = Schemas["TriageResult"];
export type Diagnosis = Schemas["Diagnosis"];
export type Evidence = NonNullable<Diagnosis["evidence"]>[number];

/** Every state, in workflow order; the check below fails the build when the API adds one. */
export const INCIDENT_STATES = [
  "RECEIVED",
  "TRIAGED",
  "DIAGNOSED",
  "AWAITING_APPROVAL",
  "APPROVED",
  "REJECTED",
  "RESOLVED",
  "ESCALATED",
  "FAILED",
] as const satisfies readonly IncidentState[];

type MissingState = Exclude<IncidentState, (typeof INCIDENT_STATES)[number]>;
const allStatesListed: [MissingState] extends [never] ? true : MissingState = true;
void allStatesListed;

/** States in which the workflow still acts or waits; the list shows them by default (A-0010). */
export const OPEN_STATES: readonly IncidentState[] = [
  "RECEIVED",
  "TRIAGED",
  "DIAGNOSED",
  "AWAITING_APPROVAL",
  "APPROVED",
];

export const APPROVER_ROLE = "incident-approver";

/** Most characters a decision comment may have (A-0006). */
export const MAX_COMMENT_LENGTH = 2000;
