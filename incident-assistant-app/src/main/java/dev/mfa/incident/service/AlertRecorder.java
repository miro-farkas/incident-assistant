package dev.mfa.incident.service;

import dev.mfa.incident.dto.AlertIngestionResult;
import dev.mfa.incident.dto.AlertIngestionResult.IncidentOpened;
import dev.mfa.incident.dto.NewIncidentDto;
import dev.mfa.incident.exception.OpenIncidentExistsException;
import dev.mfa.incident.model.Incident;
import dev.mfa.incident.repository.IncidentRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import java.util.Optional;

/**
 * The transaction of one alert ingestion. Callers use {@link AlertIngestionService}, which retries
 * around it; retries need a new transaction each, so they cannot live in this bean.
 */
@ApplicationScoped
public class AlertRecorder {

  private final IncidentRepository incidents;
  private final IncidentService incidentService;
  private final AlertOccurrenceService occurrenceService;

  public AlertRecorder(
      IncidentRepository incidents,
      IncidentService incidentService,
      AlertOccurrenceService occurrenceService) {
    this.incidents = incidents;
    this.incidentService = incidentService;
    this.occurrenceService = occurrenceService;
  }

  /**
   * Records the alert as an occurrence of the open incident with the same source and fingerprint,
   * or opens a new incident if there is none.
   *
   * @throws OpenIncidentExistsException if a concurrent transaction opened the incident first
   */
  @Transactional
  public AlertIngestionResult record(NewIncidentDto alert) {
    Optional<Incident> open =
        incidents.findOpenForUpdate(
            alert.source(), alert.fingerprint(), IncidentStateMachine.terminalStates());
    if (open.isPresent()) {
      return occurrenceService.record(open.get(), alert);
    }
    return new IncidentOpened(incidentService.create(alert));
  }
}
