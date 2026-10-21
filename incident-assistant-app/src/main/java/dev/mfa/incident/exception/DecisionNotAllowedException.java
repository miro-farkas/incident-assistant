package dev.mfa.incident.exception;

import dev.mfa.incident.model.IncidentState;
import java.util.UUID;

/** A decision was requested for an incident that is not waiting for one (A-0006). */
public final class DecisionNotAllowedException extends IncidentException {

  private final UUID incidentId;
  private final IncidentState state;

  public DecisionNotAllowedException(UUID incidentId, IncidentState state) {
    super(
        "Incident "
            + incidentId
            + " is in state "
            + state
            + "; a decision requires "
            + IncidentState.AWAITING_APPROVAL);
    this.incidentId = incidentId;
    this.state = state;
  }

  public UUID incidentId() {
    return incidentId;
  }

  public IncidentState state() {
    return state;
  }
}
