package dev.mfa.incident.dto;

import java.util.Objects;
import java.util.UUID;

/**
 * One event of {@code ops.incidents.events.v1} as forwarded to stream clients (A-0009).
 *
 * @param payload the event JSON exactly as received from the topic
 */
public record StreamEventDto(UUID eventId, String eventType, String payload) {

  public StreamEventDto {
    Objects.requireNonNull(eventId, "eventId");
    Objects.requireNonNull(eventType, "eventType");
    Objects.requireNonNull(payload, "payload");
  }
}
