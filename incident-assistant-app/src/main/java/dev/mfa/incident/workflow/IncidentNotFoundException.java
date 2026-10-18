package dev.mfa.incident.workflow;

import java.util.UUID;

public final class IncidentNotFoundException extends IncidentException {

  private final UUID incidentId;

  public IncidentNotFoundException(UUID incidentId) {
    super("Incident " + incidentId + " not found");
    this.incidentId = incidentId;
  }

  public UUID incidentId() {
    return incidentId;
  }
}
