package dev.mfa.incident.dto;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.mfa.incident.model.IncidentState;
import dev.mfa.incident.model.StepType;
import dev.mfa.incident.testsupport.IncidentEventSchema;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * The serialized {@link IncidentStateChanged} must match docs/schemas/incident-event-v1.json
 * (ADR-0012). Uses the application ObjectMapper, so Quarkus Jackson settings are covered.
 */
@QuarkusTest
class IncidentEventSchemaTest {

  private static final Instant OCCURRED_AT = Instant.parse("2026-10-18T10:15:30.123456Z");

  @Inject ObjectMapper objectMapper;

  private IncidentEventSchema schema;

  @BeforeEach
  void loadSchema() {
    schema = IncidentEventSchema.load(objectMapper);
  }

  @Test
  void creationEventMatchesSchema() {
    JsonNode json =
        objectMapper.valueToTree(event(0, null, IncidentState.RECEIVED, null, null, null));

    schema.assertConforms(json);
    assertThat(json.get("occurredAt").asText()).isEqualTo("2026-10-18T10:15:30.123456Z");
  }

  @Test
  void transitionEventWithAllFieldsMatchesSchema() {
    IncidentStateChanged event =
        event(
            3,
            IncidentState.DIAGNOSED,
            IncidentState.ESCALATED,
            StepType.PROPOSAL,
            UUID.randomUUID(),
            "NO_SAFE_ACTION");

    schema.assertConforms(objectMapper.valueToTree(event));
  }

  @Test
  void routingMetadataIsNotPartOfTheValue() {
    JsonNode json =
        objectMapper.valueToTree(event(0, null, IncidentState.RECEIVED, null, null, null));

    assertThat(json.has("messageKey")).isFalse();
  }

  @Test
  void stateEnumListsEveryIncidentState() {
    assertThat(schema.enumValues("state"))
        .containsExactlyInAnyOrderElementsOf(names(IncidentState.values()));

    List<String> previousStates = new ArrayList<>(names(IncidentState.values()));
    previousStates.add(null);
    assertThat(schema.enumValues("previousState"))
        .containsExactlyInAnyOrderElementsOf(previousStates);
  }

  @Test
  void stepEnumListsEveryStepType() {
    List<String> steps = new ArrayList<>(names(StepType.values()));
    steps.add(null);

    assertThat(schema.enumValues("step")).containsExactlyInAnyOrderElementsOf(steps);
  }

  private static IncidentStateChanged event(
      long version,
      IncidentState previousState,
      IncidentState state,
      StepType step,
      UUID stepId,
      String reasonCode) {
    return new IncidentStateChanged(
        IncidentStateChanged.SCHEMA_VERSION,
        UUID.randomUUID(),
        IncidentStateChanged.EVENT_TYPE,
        OCCURRED_AT,
        UUID.randomUUID(),
        version,
        previousState,
        state,
        step,
        stepId,
        reasonCode);
  }

  private static List<String> names(Enum<?>[] values) {
    return Arrays.stream(values).map(Enum::name).toList();
  }
}
