package dev.mfa.incident.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * Event published on {@code ops.incidents.events.v1} for every alert occurrence recorded for an
 * open incident. This record is the wire format defined in A-0012 and {@code
 * docs/schemas/incident-occurrence-event-v1.json}; changing it changes the public contract.
 */
public record IncidentOccurrenceRecorded(
    int schemaVersion,
    UUID eventId,
    String eventType,
    Instant occurredAt,
    UUID incidentId,
    long incidentVersion,
    UUID occurrenceId,
    long occurrenceCount)
    implements OutboxMessage {

  public static final int SCHEMA_VERSION = 1;
  public static final String EVENT_TYPE = "incident.occurrence-recorded";

  /** Kafka key: the incident id, so it stays in order with the incident's state events. */
  @Override
  public String messageKey() {
    return incidentId.toString();
  }
}
