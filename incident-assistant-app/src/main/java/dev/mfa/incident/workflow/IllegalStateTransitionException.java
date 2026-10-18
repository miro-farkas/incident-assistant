package dev.mfa.incident.workflow;

/** The requested state change is not allowed by {@link IncidentStateMachine}. */
public final class IllegalStateTransitionException extends IncidentException {

  private final IncidentState from;
  private final IncidentState to;

  public IllegalStateTransitionException(IncidentState from, IncidentState to) {
    super("Transition " + from + " -> " + to + " is not allowed");
    this.from = from;
    this.to = to;
  }

  public IncidentState from() {
    return from;
  }

  public IncidentState to() {
    return to;
  }
}
