package dev.mfa.incident.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.mfa.incident.dto.StreamEventDto;
import dev.mfa.incident.service.IncidentStreamService;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.UUID;
import org.eclipse.microprofile.reactive.messaging.Incoming;
import org.jboss.logging.Logger;

/**
 * Reads {@code ops.incidents.events.v1} for the event stream of this instance (A-0009). Every
 * instance has a consumer group of its own, starts at the latest offset and commits nothing
 * (configured in {@code application.properties}), so it sees every event and none twice per run.
 * Only {@code eventId} and {@code eventType} are read; the payload is forwarded unchanged.
 */
@ApplicationScoped
public class IncidentEventStreamConsumer {

  private static final Logger LOG = Logger.getLogger(IncidentEventStreamConsumer.class);

  private final IncidentStreamService stream;
  private final ObjectMapper objectMapper;

  public IncidentEventStreamConsumer(IncidentStreamService stream, ObjectMapper objectMapper) {
    this.stream = stream;
    this.objectMapper = objectMapper;
  }

  /** An unreadable event is skipped with a warning; it never stops the channel. */
  @Incoming("incident-events-stream")
  public void consume(String payload) {
    try {
      JsonNode event = objectMapper.readTree(payload);
      UUID eventId = UUID.fromString(event.path("eventId").asText());
      String eventType = event.path("eventType").asText();
      if (eventType.isEmpty()) {
        LOG.warnf("Skipped event %s without eventType", eventId);
        return;
      }
      stream.publish(new StreamEventDto(eventId, eventType, payload));
    } catch (JsonProcessingException | IllegalArgumentException e) {
      LOG.warnf("Skipped unreadable incident event (%d characters)", payload.length());
    }
  }
}
