package dev.mfa.incident.dto;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.mfa.incident.testsupport.IncidentEventSchema;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * The serialized {@link IncidentOccurrenceRecorded} must match
 * docs/schemas/incident-occurrence-event-v1.json (A-0012). Uses the application ObjectMapper, so
 * Quarkus Jackson settings are covered.
 */
@QuarkusTest
class IncidentOccurrenceEventSchemaTest {

  private static final Instant OCCURRED_AT = Instant.parse("2026-10-22T10:15:30.123456Z");

  @Inject ObjectMapper objectMapper;

  private IncidentEventSchema schema;

  @BeforeEach
  void loadSchema() {
    schema = IncidentEventSchema.loadOccurrence(objectMapper);
  }

  @Test
  void occurrenceEventMatchesSchema() {
    JsonNode json = objectMapper.valueToTree(event(UUID.randomUUID()));

    schema.assertConforms(json);
    assertThat(json.get("eventType").asText()).isEqualTo("incident.occurrence-recorded");
    assertThat(json.get("occurredAt").asText()).isEqualTo("2026-10-22T10:15:30.123456Z");
  }

  @Test
  void keyIsTheIncidentIdAndNotPartOfTheValue() {
    UUID incidentId = UUID.randomUUID();
    IncidentOccurrenceRecorded event = event(incidentId);

    JsonNode json = objectMapper.valueToTree(event);

    assertThat(event.messageKey()).isEqualTo(incidentId.toString());
    assertThat(json.has("messageKey")).isFalse();
  }

  private static IncidentOccurrenceRecorded event(UUID incidentId) {
    return new IncidentOccurrenceRecorded(
        IncidentOccurrenceRecorded.SCHEMA_VERSION,
        UUID.randomUUID(),
        IncidentOccurrenceRecorded.EVENT_TYPE,
        OCCURRED_AT,
        incidentId,
        2,
        UUID.randomUUID(),
        3);
  }
}
