package dev.mfa.incident.dto;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** docs/schemas/alert-v1.json and the constraints of {@link AlertMessage} must agree (A-0003). */
class AlertSchemaTest {

  private static JsonNode schema;

  @BeforeAll
  static void loadSchema() throws IOException {
    try (InputStream in = AlertSchemaTest.class.getResourceAsStream("/schemas/alert-v1.json")) {
      assertThat(in).isNotNull();
      schema = new ObjectMapper().readTree(in);
    }
  }

  @Test
  void documentsExactlyTheRecordComponents() {
    Set<String> documented = new TreeSet<>();
    schema.get("properties").fieldNames().forEachRemaining(documented::add);

    assertThat(documented)
        .isEqualTo(
            Arrays.stream(AlertMessage.class.getRecordComponents())
                .map(RecordComponent::getName)
                .collect(Collectors.toCollection(TreeSet::new)));
  }

  @Test
  void requiredPropertiesAreTheMandatoryComponents() {
    Set<String> required = new TreeSet<>();
    schema.get("required").forEach(name -> required.add(name.asText()));

    Set<String> mandatory =
        Arrays.stream(AlertMessage.class.getRecordComponents())
            .filter(
                c ->
                    accessor(c).isAnnotationPresent(NotNull.class)
                        || accessor(c).isAnnotationPresent(NotBlank.class))
            .map(RecordComponent::getName)
            .collect(Collectors.toCollection(TreeSet::new));

    assertThat(required).isEqualTo(mandatory);
  }

  @Test
  void maxLengthsMatchSizeConstraints() {
    Map<String, Integer> documented = new TreeMap<>();
    schema
        .get("properties")
        .properties()
        .forEach(
            p -> {
              if (p.getValue().has("maxLength")) {
                documented.put(p.getKey(), p.getValue().get("maxLength").asInt());
              }
            });

    Map<String, Integer> constrained = new TreeMap<>();
    for (RecordComponent component : AlertMessage.class.getRecordComponents()) {
      Size size = accessor(component).getAnnotation(Size.class);
      if (size != null && component.getType() == String.class) {
        constrained.put(component.getName(), size.max());
      }
    }

    assertThat(documented).isEqualTo(constrained);
  }

  @Test
  void labelLimitsMatchConstants() {
    JsonNode labels = schema.get("properties").get("labels");

    assertThat(labels.get("maxProperties").asInt()).isEqualTo(AlertMessage.MAX_LABELS);
    assertThat(labels.get("propertyNames").get("maxLength").asInt())
        .isEqualTo(AlertMessage.MAX_LABEL_KEY_LENGTH);
    assertThat(labels.get("additionalProperties").get("maxLength").asInt())
        .isEqualTo(AlertMessage.MAX_LABEL_VALUE_LENGTH);
  }

  private static Method accessor(RecordComponent component) {
    return component.getAccessor();
  }
}
