package dev.mfa.incident.notification.dto;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * The fields of an {@code incident.state-changed} event (A-0007, {@code
 * docs/schemas/incident-event-v1.json}) that the service uses; other fields are ignored.
 *
 * @param reasonCode why the state was entered; {@code null} when the event has none
 */
public record IncidentStateEventDto(
    UUID eventId,
    Instant occurredAt,
    UUID incidentId,
    long incidentVersion,
    String state,
    String reasonCode) {

  public static final String EVENT_TYPE = "incident.state-changed";

  public IncidentStateEventDto {
    Objects.requireNonNull(eventId, "eventId");
    Objects.requireNonNull(occurredAt, "occurredAt");
    Objects.requireNonNull(incidentId, "incidentId");
    Objects.requireNonNull(state, "state");
    if (incidentVersion < 0) {
      throw new IllegalArgumentException("incidentVersion must not be negative");
    }
  }
}
