package dev.mfa.incident.mapper;

import dev.mfa.incident.dto.IncidentOccurrenceRecorded;
import dev.mfa.incident.dto.IncidentStateChanged;
import dev.mfa.incident.dto.ReasonCode;
import dev.mfa.incident.model.AlertOccurrence;
import dev.mfa.incident.model.Incident;
import dev.mfa.incident.model.IncidentState;
import dev.mfa.incident.model.StepType;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.UUID;

/**
 * Builds A-0007 state events and A-0012 occurrence events from flushed entities, so that version
 * and timestamps are the values stored in the database.
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

  /**
   * Occurrence event (A-0012) for a flushed occurrence row.
   *
   * @param occurrenceCount occurrences of the incident including this one
   */
  public IncidentOccurrenceRecorded occurrenceRecorded(
      Incident incident, AlertOccurrence occurrence, long occurrenceCount) {
    return new IncidentOccurrenceRecorded(
        IncidentOccurrenceRecorded.SCHEMA_VERSION,
        UUID.randomUUID(),
        IncidentOccurrenceRecorded.EVENT_TYPE,
        occurrence.getCreatedAt(),
        incident.getId(),
        incident.getVersion(),
        occurrence.getId(),
        occurrenceCount);
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
