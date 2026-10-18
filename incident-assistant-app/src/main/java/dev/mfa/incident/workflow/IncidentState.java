package dev.mfa.incident.workflow;

/**
 * Workflow state of an incident. Allowed transitions are defined only in {@link
 * IncidentStateMachine}.
 */
public enum IncidentState {
  RECEIVED,
  TRIAGED,
  DIAGNOSED,
  AWAITING_APPROVAL,
  APPROVED,
  REJECTED,
  RESOLVED,
  ESCALATED,
  FAILED
}
