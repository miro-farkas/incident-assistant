package dev.mfa.incident.mapper;

import dev.mfa.incident.dto.IncidentDto;
import dev.mfa.incident.dto.NewIncidentDto;
import dev.mfa.incident.model.Incident;
import jakarta.enterprise.context.ApplicationScoped;

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
}
