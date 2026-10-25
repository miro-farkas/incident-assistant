package dev.mfa.incident.notification.testsupport;

import java.time.Instant;
import java.util.UUID;

/** Event payloads as incident-assistant-app publishes them (A-0007, A-0012). */
public final class IncidentEvents {

  public static final String TOPIC = "ops.incidents.events.v1";

  private IncidentEvents() {}

  /** An {@code incident.state-changed} event; {@code reasonCode} may be {@code null}. */
  public static String stateChanged(
      UUID eventId, UUID incidentId, long version, String state, String reasonCode) {
    return """
    {"schemaVersion": 1, "eventId": "%s", "eventType": "incident.state-changed",
     "occurredAt": "%s", "incidentId": "%s", "incidentVersion": %d,
     "previousState": "DIAGNOSED", "state": "%s", "step": "PROPOSAL",
     "stepId": "%s", "reasonCode": %s}
    """
        .formatted(
            eventId,
            Instant.parse("2026-10-18T10:05:00Z"),
            incidentId,
            version,
            state,
            UUID.randomUUID(),
            reasonCode == null ? "null" : "\"" + reasonCode + "\"");
  }

  public static String occurrenceRecorded(UUID eventId, UUID incidentId, long count) {
    return """
    {"schemaVersion": 1, "eventId": "%s", "eventType": "incident.occurrence-recorded",
     "occurredAt": "2026-10-18T10:06:00Z", "incidentId": "%s", "incidentVersion": 1,
     "occurrenceId": "%s", "occurrenceCount": %d}
    """
        .formatted(eventId, incidentId, UUID.randomUUID(), count);
  }
}
