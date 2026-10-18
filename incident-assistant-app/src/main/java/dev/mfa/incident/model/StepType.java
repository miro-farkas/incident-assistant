package dev.mfa.incident.model;

/**
 * Workflow steps that are recorded as {@code incident_step} rows. The state each step runs in is
 * defined by {@code dev.mfa.incident.service.IncidentStateMachine}.
 */
public enum StepType {
  TRIAGE,
  DIAGNOSIS,
  PROPOSAL,
  EXECUTION
}
