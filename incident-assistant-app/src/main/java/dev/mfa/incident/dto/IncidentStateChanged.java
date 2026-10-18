package dev.mfa.incident.dto;

import dev.mfa.incident.model.IncidentState;
import dev.mfa.incident.model.StepType;
import java.time.Instant;
import java.util.UUID;

/**
 * Event published on {@code ops.incidents.events.v1} for incident creation and every state
 * transition. This record is the wire format defined in ADR-0012 and {@code
 * docs/schemas/incident-event-v1.json}; changing it changes the public contract.
 */
public record IncidentStateChanged(
    int schemaVersion,
    UUID eventId,
    String eventType,
    Instant occurredAt,
    UUID incidentId,
    long incidentVersion,
    IncidentState previousState,
    IncidentState state,
    StepType step,
    UUID stepId,
    String reasonCode)
    implements OutboxMessage {

  public static final int SCHEMA_VERSION = 1;
  public static final String EVENT_TYPE = "incident.state-changed";

  /** Kafka key: the incident id, so all events of one incident stay in order (ADR-0004). */
  @Override
  public String messageKey() {
    return incidentId.toString();
  }
}
