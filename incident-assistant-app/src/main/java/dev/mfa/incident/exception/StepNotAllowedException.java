package dev.mfa.incident.exception;

import dev.mfa.incident.model.IncidentState;
import dev.mfa.incident.model.StepType;

/** The step cannot run for an incident in its current state. */
public final class StepNotAllowedException extends IncidentException {

  private final StepType stepType;
  private final IncidentState state;

  public StepNotAllowedException(
      StepType stepType, IncidentState state, IncidentState requiredState) {
    super("Step " + stepType + " requires state " + requiredState + ", was " + state);
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
