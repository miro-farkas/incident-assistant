package dev.mfa.incident.workflow;

/** The step cannot run for an incident in its current state. */
public final class StepNotAllowedException extends IncidentException {

  private final StepType stepType;
  private final IncidentState state;

  public StepNotAllowedException(StepType stepType, IncidentState state) {
    super("Step " + stepType + " requires state " + stepType.sourceState() + ", was " + state);
    this.stepType = stepType;
    this.state = state;
  }

  public StepType stepType() {
    return stepType;
  }

  public IncidentState state() {
    return state;
  }
}
