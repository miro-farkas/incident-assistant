package dev.mfa.incident.mapper;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.mfa.incident.dto.AlertMessage;
import dev.mfa.incident.dto.NewIncidentDto;
import dev.mfa.incident.model.AlertOccurrence;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.UUID;

@ApplicationScoped
public class AlertMapper {

  private final ObjectMapper objectMapper;

  public AlertMapper(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  /**
   * The stored alert payload is the validated {@link AlertMessage} serialized again, not the
   * received bytes: properties outside the schema are not kept.
   */
  public NewIncidentDto toNewIncident(AlertMessage alert) {
    return new NewIncidentDto(alert.source(), alert.fingerprint(), serialize(alert));
  }

  public AlertOccurrence toOccurrence(UUID incidentId, NewIncidentDto alert) {
    AlertOccurrence occurrence = new AlertOccurrence();
    occurrence.setIncidentId(incidentId);
    occurrence.setAlertPayload(alert.alertPayload());
    return occurrence;
  }

  private String serialize(AlertMessage alert) {
    try {
      return objectMapper.writeValueAsString(alert);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("Cannot serialize alert", e);
    }
  }
}
