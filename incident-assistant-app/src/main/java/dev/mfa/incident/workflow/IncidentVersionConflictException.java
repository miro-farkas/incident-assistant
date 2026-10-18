package dev.mfa.incident.workflow;

import java.util.UUID;

/**
 * The incident was changed since the caller read it (optimistic locking). The caller must reload
 * the incident and decide again.
 */
public final class IncidentVersionConflictException extends IncidentException {

  private final UUID incidentId;
  private final long expectedVersion;

  public IncidentVersionConflictException(UUID incidentId, long expectedVersion) {
    super("Incident " + incidentId + " is no longer at version " + expectedVersion);
    this.incidentId = incidentId;
    this.expectedVersion = expectedVersion;
  }

  public UUID incidentId() {
    return incidentId;
  }

  public long expectedVersion() {
    return expectedVersion;
  }
}
