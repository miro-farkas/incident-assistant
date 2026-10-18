package dev.mfa.incident.workflow;

/** Workflow steps that are recorded as {@code incident_step} rows. */
public enum StepType {
  TRIAGE(IncidentState.RECEIVED),
  DIAGNOSIS(IncidentState.TRIAGED),
  PROPOSAL(IncidentState.DIAGNOSED),
  EXECUTION(IncidentState.APPROVED);

  private final IncidentState sourceState;

  StepType(IncidentState sourceState) {
    this.sourceState = sourceState;
  }

  /** The only state in which this step may run. */
  public IncidentState sourceState() {
    return sourceState;
  }
}
