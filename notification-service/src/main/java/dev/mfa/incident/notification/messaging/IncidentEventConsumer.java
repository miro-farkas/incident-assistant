package dev.mfa.incident.notification.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.mfa.incident.notification.dto.EventRecordingResult;
import dev.mfa.incident.notification.dto.IncidentStateEventDto;
import dev.mfa.incident.notification.service.EventIngestionService;
import io.smallrye.common.annotation.RunOnVirtualThread;
import jakarta.enterprise.context.ApplicationScoped;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.UUID;
import java.util.regex.Pattern;
import org.eclipse.microprofile.reactive.messaging.Incoming;
import org.jboss.logging.Logger;

/**
 * Consumes {@code ops.incidents.events.v1} with group {@code notification-service} (A-0011). A
 * record is acknowledged, and its offset committed, after its event is recorded. Events of other
 * types, such as {@code incident.occurrence-recorded}, are skipped without being recorded; an
 * unreadable event is skipped with a warning that does not include the payload.
 */
@ApplicationScoped
public class IncidentEventConsumer {

  private static final Logger LOG = Logger.getLogger(IncidentEventConsumer.class);

  /** Patterns of {@code docs/schemas/incident-event-v1.json}; they also bound the column sizes. */
  private static final Pattern STATE = Pattern.compile("[A-Z][A-Z_]{0,31}");

  private static final Pattern REASON_CODE = Pattern.compile("[A-Z][A-Z0-9_]{0,63}");

  private final EventIngestionService ingestion;
  private final ObjectMapper objectMapper;

  public IncidentEventConsumer(EventIngestionService ingestion, ObjectMapper objectMapper) {
    this.ingestion = ingestion;
    this.objectMapper = objectMapper;
  }

  @Incoming("incident-events")
  @RunOnVirtualThread
  public void consume(String payload) {
    IncidentStateEventDto event;
    try {
      JsonNode json = objectMapper.readTree(payload);
      if (!IncidentStateEventDto.EVENT_TYPE.equals(json.path("eventType").asText())) {
        return;
      }
      event = read(json);
    } catch (JsonProcessingException | IllegalArgumentException | DateTimeParseException e) {
      LOG.warnf("Skipped unreadable incident event (%d characters)", payload.length());
      return;
    }
    switch (ingestion.ingest(event)) {
      case EventRecordingResult.Recorded recorded ->
          LOG.debugf(
              "Recorded event %s of incident %s: %d notifications",
              event.eventId(), event.incidentId(), recorded.notifications());
      case EventRecordingResult.Outdated outdated ->
          LOG.debugf(
              "Skipped event %s of incident %s: version %d, seen up to %d",
              event.eventId(), event.incidentId(), event.incidentVersion(), outdated.lastVersion());
    }
  }

  private static IncidentStateEventDto read(JsonNode json) {
    JsonNode version = json.path("incidentVersion");
    if (!version.canConvertToExactIntegral()) {
      throw new IllegalArgumentException("incidentVersion is not an integer");
    }
    JsonNode reasonCode = json.path("reasonCode");
    return new IncidentStateEventDto(
        UUID.fromString(json.path("eventId").asText()),
        Instant.parse(json.path("occurredAt").asText()),
        UUID.fromString(json.path("incidentId").asText()),
        version.asLong(),
        matching(json.path("state"), STATE, "state"),
        reasonCode.isNull() || reasonCode.isMissingNode()
            ? null
            : matching(reasonCode, REASON_CODE, "reasonCode"));
  }

  private static String matching(JsonNode node, Pattern pattern, String field) {
    if (!node.isTextual() || !pattern.matcher(node.asText()).matches()) {
      throw new IllegalArgumentException(field + " is missing or invalid");
    }
    return node.asText();
  }
}
