package dev.mfa.incident.dto;

import java.util.UUID;

/** Outcome of ingesting one alert (A-0003). */
public sealed interface AlertIngestionResult {

  UUID incidentId();

  /** No incident was open for the alert, so a new one was created. */
  record IncidentOpened(IncidentDto incident) implements AlertIngestionResult {

    @Override
    public UUID incidentId() {
      return incident.id();
    }
  }

  /** An incident was already open for the alert; the alert was recorded as an occurrence of it. */
  record OccurrenceRecorded(UUID incidentId, UUID occurrenceId) implements AlertIngestionResult {}
}
