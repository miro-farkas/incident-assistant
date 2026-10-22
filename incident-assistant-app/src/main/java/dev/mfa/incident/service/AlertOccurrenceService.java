package dev.mfa.incident.service;

import dev.mfa.incident.dto.AlertIngestionResult.OccurrenceRecorded;
import dev.mfa.incident.dto.NewIncidentDto;
import dev.mfa.incident.mapper.AlertMapper;
import dev.mfa.incident.mapper.IncidentEventMapper;
import dev.mfa.incident.model.AlertOccurrence;
import dev.mfa.incident.model.Incident;
import dev.mfa.incident.repository.AlertOccurrenceRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

/**
 * Records repeated alerts of an open incident (A-0003) and publishes each one as an occurrence
 * event through the outbox (A-0012).
 */
@ApplicationScoped
public class AlertOccurrenceService {

  private final AlertOccurrenceRepository occurrences;
  private final OutboxWriter outbox;
  private final AlertMapper alertMapper;
  private final IncidentEventMapper eventMapper;

  public AlertOccurrenceService(
      AlertOccurrenceRepository occurrences,
      OutboxWriter outbox,
      AlertMapper alertMapper,
      IncidentEventMapper eventMapper) {
    this.occurrences = occurrences;
    this.outbox = outbox;
    this.alertMapper = alertMapper;
    this.eventMapper = eventMapper;
  }

  /**
   * Inserts the occurrence and its event in the caller's transaction. The caller holds the
   * incident's row lock, so the count includes exactly the occurrences committed before and this
   * one.
   */
  @Transactional(Transactional.TxType.MANDATORY)
  OccurrenceRecorded record(Incident incident, NewIncidentDto alert) {
    AlertOccurrence occurrence = alertMapper.toOccurrence(incident.getId(), alert);
    occurrences.persistAndFlush(occurrence);
    long count = occurrences.countByIncident(incident.getId());
    outbox.write(eventMapper.occurrenceRecorded(incident, occurrence, count));
    return new OccurrenceRecorded(incident.getId(), occurrence.getId());
  }
}
