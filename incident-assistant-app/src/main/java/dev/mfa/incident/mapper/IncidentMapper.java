package dev.mfa.incident.mapper;

import dev.mfa.incident.dto.ApprovalDecisionDto;
import dev.mfa.incident.dto.IncidentDetailDto;
import dev.mfa.incident.dto.IncidentDto;
import dev.mfa.incident.dto.IncidentSummaryDto;
import dev.mfa.incident.dto.NewIncidentDto;
import dev.mfa.incident.dto.StepDto;
import dev.mfa.incident.model.Incident;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.List;

@ApplicationScoped
public class IncidentMapper {

  /** Copies the alert data; the state is set by the service. */
  public Incident toEntity(NewIncidentDto newIncident) {
    Incident incident = new Incident();
    incident.setSource(newIncident.source());
    incident.setFingerprint(newIncident.fingerprint());
    incident.setAlertPayload(newIncident.alertPayload());
    return incident;
  }

  public IncidentDto toDto(Incident incident) {
    return new IncidentDto(
        incident.getId(),
        incident.getSource(),
        incident.getFingerprint(),
        incident.getState(),
        incident.getVersion(),
        incident.getCreatedAt(),
        incident.getUpdatedAt());
  }

  public IncidentSummaryDto toSummaryDto(Incident incident, long occurrenceCount) {
    return new IncidentSummaryDto(
        incident.getId(),
        incident.getSource(),
        incident.getFingerprint(),
        incident.getState(),
        incident.getVersion(),
        incident.getCreatedAt(),
        incident.getUpdatedAt(),
        occurrenceCount);
  }

  /**
   * @param decision {@code null} when no decision was made
   */
  public IncidentDetailDto toDetailDto(
      Incident incident, long occurrenceCount, List<StepDto> steps, ApprovalDecisionDto decision) {
    return new IncidentDetailDto(
        incident.getId(),
        incident.getSource(),
        incident.getFingerprint(),
        incident.getState(),
        incident.getVersion(),
        incident.getCreatedAt(),
        incident.getUpdatedAt(),
        incident.getAlertPayload(),
        occurrenceCount,
        steps,
        decision);
  }
}
