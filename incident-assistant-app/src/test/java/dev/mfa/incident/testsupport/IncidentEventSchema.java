package dev.mfa.incident.testsupport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Checks events against the schemas of the event types on {@code ops.incidents.events.v1}: {@code
 * docs/schemas/incident-event-v1.json} (A-0007) and {@code
 * docs/schemas/incident-occurrence-event-v1.json} (A-0012). Supports the subset of JSON Schema
 * keywords the documents use: type, const, enum, pattern, format (uuid, date-time), minimum and
 * required.
 */
public final class IncidentEventSchema {

  private static final String STATE_EVENT = "/schemas/incident-event-v1.json";
  private static final String OCCURRENCE_EVENT = "/schemas/incident-occurrence-event-v1.json";

  private final JsonNode schema;

  private IncidentEventSchema(JsonNode schema) {
    this.schema = schema;
  }

  /** Schema of {@code incident.state-changed}. */
  public static IncidentEventSchema load(ObjectMapper objectMapper) {
    return load(objectMapper, STATE_EVENT);
  }

  /** Schema of {@code incident.occurrence-recorded}. */
  public static IncidentEventSchema loadOccurrence(ObjectMapper objectMapper) {
    return load(objectMapper, OCCURRENCE_EVENT);
  }

  private static IncidentEventSchema load(ObjectMapper objectMapper, String resource) {
    try (InputStream in = IncidentEventSchema.class.getResourceAsStream(resource)) {
      assertThat(in).as("schema resource %s", resource).isNotNull();
      return new IncidentEventSchema(objectMapper.readTree(in));
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  public Set<String> propertyNames() {
    return fieldNames(schema.get("properties"));
  }

  public List<String> enumValues(String property) {
    List<String> values = new ArrayList<>();
    schema.get("properties").get(property).get("enum").forEach(v -> values.add(v.asText(null)));
    return values;
  }

  /** Asserts that the producer emits exactly the documented properties, each valid. */
  public void assertConforms(JsonNode event) {
    assertThat(event.isObject()).as("event is a JSON object").isTrue();
    assertThat(fieldNames(event)).as("event properties").isEqualTo(propertyNames());
    schema.get("required").forEach(r -> assertThat(event.has(r.asText())).as(r.asText()).isTrue());
    schema
        .get("properties")
        .properties()
        .forEach(p -> assertProperty(p.getKey(), p.getValue(), event.get(p.getKey())));
  }

  private static void assertProperty(String name, JsonNode rule, JsonNode value) {
    assertThat(allowedTypes(rule)).as("type of %s", name).contains(typeOf(value));
    if (rule.has("const")) {
      assertThat(value).as(name).isEqualTo(rule.get("const"));
    }
    if (rule.has("enum")) {
      List<JsonNode> allowed = new ArrayList<>();
      rule.get("enum").forEach(allowed::add);
      assertThat(allowed).as("enum of %s", name).contains(value);
    }
    if (value.isNull()) {
      return;
    }
    if (rule.has("pattern")) {
      assertThat(Pattern.compile(rule.get("pattern").asText()).matcher(value.asText()).find())
          .as("pattern of %s: %s", name, value)
          .isTrue();
    }
    if (rule.has("minimum")) {
      assertThat(value.asLong()).as(name).isGreaterThanOrEqualTo(rule.get("minimum").asLong());
    }
    if (rule.has("format")) {
      switch (rule.get("format").asText()) {
        case "uuid" ->
            assertThatCode(() -> UUID.fromString(value.asText()))
                .as(name)
                .doesNotThrowAnyException();
        case "date-time" ->
            assertThatCode(() -> OffsetDateTime.parse(value.asText()))
                .as(name)
                .doesNotThrowAnyException();
        default -> throw new IllegalStateException("Unsupported format in schema: " + rule);
      }
    }
  }

  private static Set<String> allowedTypes(JsonNode rule) {
    JsonNode type = rule.get("type");
    Set<String> types = new TreeSet<>();
    if (type.isArray()) {
      type.forEach(t -> types.add(t.asText()));
    } else {
      types.add(type.asText());
    }
    return types;
  }

  private static String typeOf(JsonNode value) {
    return switch (value.getNodeType()) {
      case NULL -> "null";
      case STRING -> "string";
      case NUMBER -> value.isIntegralNumber() ? "integer" : "number";
      case BOOLEAN -> "boolean";
      case OBJECT -> "object";
      case ARRAY -> "array";
      default -> value.getNodeType().name();
    };
  }

  private static Set<String> fieldNames(JsonNode node) {
    Set<String> names = new TreeSet<>();
    node.fieldNames().forEachRemaining(names::add);
    return names;
  }
}
