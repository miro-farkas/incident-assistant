# A-0001: Incident workflow

- Status: Accepted
- Date: 2026-10-18
- Applies: P-0001

## Context
Incident handling requires auditability, predictable cost, safe execution of remediation
actions, and a human approval that may arrive minutes or hours later.

## Decision
- The incident workflow is the state machine in `docs/architecture.md`, defined in code
  only in `IncidentStateMachine`.
- Explicit steps: triage, remediation proposal, approval and execution, each with typed
  inputs and outputs.
- Diagnosis is the only autonomous step: an AI service that may iterate over the knowledge
  base (A-0004) and read-only ops tools (A-0005), bounded by a maximum number of tool calls
  and a wall-clock timeout.
- Every step runs in exactly one state (`IncidentStateMachine.sourceState`): triage in
  `RECEIVED`, diagnosis in `TRIAGED`, proposal in `DIAGNOSED`, execution in `APPROVED`.
- Any step failure or exceeded budget moves the incident to `ESCALATED` with a reason code
  (`FAILED` for a failed execution), never into a silent retry loop. Entering `ESCALATED`,
  `FAILED` or `REJECTED` requires a reason code; other transitions take none.

## Alternatives considered
- See P-0001 (autonomous agent, BPMN engine, agentic workflow API).

## Consequences
- Less flexibility for unforeseen incident types; mitigated by the autonomous diagnosis.
- Recovery of expired leases and approval timeouts is backlog item INC-011.
