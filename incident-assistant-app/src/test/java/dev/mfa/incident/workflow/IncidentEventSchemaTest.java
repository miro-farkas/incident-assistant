package dev.mfa.incident.workflow;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
    IncidentStateChanged event =
        IncidentStateChanged.created(incident(IncidentState.RECEIVED, 0), OCCURRED_AT);

    JsonNode json = objectMapper.valueToTree(event);

    schema.assertConforms(json);
    assertThat(json.get("occurredAt").asText()).isEqualTo("2026-10-18T10:15:30.123456Z");
  }

  @Test
  void transitionEventWithAllFieldsMatchesSchema() {
    IncidentStateChanged event =
        IncidentStateChanged.transitioned(
            incident(IncidentState.ESCALATED, 3),
            IncidentState.DIAGNOSED,
            StepType.PROPOSAL,
            UUID.randomUUID(),
            new ReasonCode("NO_SAFE_ACTION"),
            OCCURRED_AT);

    schema.assertConforms(objectMapper.valueToTree(event));
  }

  @Test
  void routingMetadataIsNotPartOfTheValue() {
    JsonNode json =
        objectMapper.valueToTree(
            IncidentStateChanged.created(incident(IncidentState.RECEIVED, 0), OCCURRED_AT));

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

  private static Incident incident(IncidentState state, long version) {
    return new Incident(
        UUID.randomUUID(), WorkflowFixtures.SOURCE, "fp", state, version, OCCURRED_AT, OCCURRED_AT);
  }

  private static List<String> names(Enum<?>[] values) {
    return Arrays.stream(values).map(Enum::name).toList();
  }
}
