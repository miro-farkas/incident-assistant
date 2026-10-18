package dev.mfa.incident.model;

/**
 * Workflow state of an incident. Allowed transitions are defined only in {@code
 * dev.mfa.incident.service.IncidentStateMachine}.
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
