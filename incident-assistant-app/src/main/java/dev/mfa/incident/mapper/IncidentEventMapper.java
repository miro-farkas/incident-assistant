package dev.mfa.incident.mapper;

import dev.mfa.incident.dto.IncidentStateChanged;
import dev.mfa.incident.dto.ReasonCode;
import dev.mfa.incident.model.Incident;
import dev.mfa.incident.model.IncidentState;
import dev.mfa.incident.model.StepType;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.UUID;

/**
 * Builds A-0007 state events from a flushed incident, so that version and timestamps are the values
 * stored in the database.
 */
@ApplicationScoped
public class IncidentEventMapper {

  public IncidentStateChanged created(Incident incident) {
    return new IncidentStateChanged(
        IncidentStateChanged.SCHEMA_VERSION,
        UUID.randomUUID(),
        IncidentStateChanged.EVENT_TYPE,
        incident.getCreatedAt(),
        incident.getId(),
        incident.getVersion(),
        null,
        incident.getState(),
        null,
        null,
        null);
  }

  public IncidentStateChanged transitioned(
      Incident incident,
      IncidentState previousState,
      StepType step,
      UUID stepId,
      ReasonCode reasonCode) {
    return new IncidentStateChanged(
        IncidentStateChanged.SCHEMA_VERSION,
        UUID.randomUUID(),
        IncidentStateChanged.EVENT_TYPE,
        incident.getUpdatedAt(),
        incident.getId(),
        incident.getVersion(),
        previousState,
        incident.getState(),
        step,
        stepId,
        reasonCode == null ? null : reasonCode.value());
  }
}
