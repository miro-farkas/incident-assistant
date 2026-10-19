package dev.mfa.incident.service;

import dev.mfa.incident.dto.AlertIngestionResult;
import dev.mfa.incident.dto.AlertMessage;
import dev.mfa.incident.dto.NewIncidentDto;
import dev.mfa.incident.exception.OpenIncidentExistsException;
import dev.mfa.incident.mapper.AlertMapper;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.faulttolerance.Retry;

/**
 * Ingests validated alerts (A-0003): opens an incident in {@code RECEIVED}, or records the alert as
 * an occurrence of the incident already open for it. Ingesting the same alert twice therefore never
 * creates a second open incident, which makes redelivered Kafka records harmless.
 */
@ApplicationScoped
public class AlertIngestionService {

  private final AlertRecorder recorder;
  private final AlertMapper alertMapper;

  public AlertIngestionService(AlertRecorder recorder, AlertMapper alertMapper) {
    this.recorder = recorder;
    this.alertMapper = alertMapper;
  }

  /**
   * Failures such as a lost database connection are retried, each attempt in a new transaction
   * (limits in {@code application.properties}); when the retries are used up the exception reaches
   * the caller.
   */
  @Retry(abortOn = IllegalArgumentException.class)
  public AlertIngestionResult ingest(AlertMessage alert) {
    NewIncidentDto newIncident = alertMapper.toNewIncident(alert);
    try {
      return recorder.record(newIncident);
    } catch (OpenIncidentExistsException e) {
      // The same alert was ingested concurrently and its incident committed first; the second
      // attempt sees that incident and records an occurrence.
      return recorder.record(newIncident);
    }
  }
}
