package dev.mfa.incident.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.mfa.incident.config.AlertIngestionConfig;
import dev.mfa.incident.dto.AlertMessage;
import dev.mfa.incident.exception.InvalidAlertException;
import dev.mfa.incident.testsupport.WorkflowFixtures;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.nio.charset.StandardCharsets;
import org.assertj.core.api.AbstractThrowableAssert;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Parsing and validation of alert payloads with the application's ObjectMapper and Validator. Every
 * rejection must name the rule without echoing received content, which is marked with {@link
 * #SECRET}.
 */
@QuarkusTest
class AlertPayloadReaderTest {

  private static final String SECRET = "s3cr3t-content";

  @Inject AlertPayloadReader reader;
  @Inject ObjectMapper objectMapper;
  @Inject AlertIngestionConfig config;

  @Test
  void readsAlertWithAllProperties() throws Exception {
    AlertMessage alert = WorkflowFixtures.alert();

    assertThat(reader.read(objectMapper.writeValueAsBytes(alert))).isEqualTo(alert);
  }

  @Test
  void readsAlertWithOnlyRequiredProperties() {
    AlertMessage alert =
        reader.read(
            bytes(
                """
                {"source": "prometheus", "fingerprint": "fp-1", "alertName": "HighErrorRate",
                 "summary": "Error rate above 5%", "startsAt": "2026-10-18T09:58:00Z"}
                """));

    assertThat(alert.fingerprint()).isEqualTo("fp-1");
    assertThat(alert.severity()).isNull();
    assertThat(alert.labels()).isNull();
  }

  @Test
  void ignoresUnknownProperties() {
    ObjectNode json = validJson();
    json.put("annotations", SECRET);

    assertThat(reader.read(bytes(json)).summary()).isEqualTo(WorkflowFixtures.alert().summary());
  }

  @Test
  void rejectsEmptyPayload() {
    assertRejected(new byte[0]).hasMessage("Alert payload is empty");
    assertRejected(null).hasMessage("Alert payload is empty");
  }

  @Test
  void rejectsOversizedPayloadWithoutParsingIt() {
    byte[] payload = new byte[config.maxPayloadBytes() + 1];

    assertRejected(payload)
        .hasMessage(
            "Alert payload has " + payload.length + " bytes, limit is " + config.maxPayloadBytes());
  }

  @Test
  void acceptsPayloadAtTheSizeLimit() {
    ObjectNode json = validJson();
    int padding = config.maxPayloadBytes() - bytes(json).length - "\"pad\":\"\",".length();
    json.put("pad", "x".repeat(padding));

    assertThat(bytes(json)).hasSize(config.maxPayloadBytes());
    assertThat(reader.read(bytes(json))).isNotNull();
  }

  @Test
  void rejectsMalformedJsonWithPositionOnly() {
    assertRejected(bytes("{\"source\": \"" + SECRET + "\",\n \"fingerprint\" " + SECRET))
        .hasMessageStartingWith("Alert payload is not valid JSON (line 2, column ");
  }

  @Test
  void rejectsTrailingContent() {
    assertRejected(bytes(validJson().toString() + " {\"" + SECRET + "\": 1}"))
        .hasMessageStartingWith("Alert payload must be a single JSON object");
  }

  @ParameterizedTest
  @ValueSource(strings = {"[\"" + SECRET + "\"]", "\"" + SECRET + "\"", "42"})
  void rejectsJsonThatIsNotAnObject(String payload) {
    assertRejected(bytes(payload)).hasMessageStartingWith("Alert payload must be a single JSON");
  }

  @Test
  void rejectsJsonNull() {
    assertRejected(bytes("null")).hasMessage("Alert payload must be a JSON object");
  }

  @Test
  void rejectsPropertyOfWrongTypeNamingOnlyTheProperty() {
    ObjectNode json = validJson();
    json.put("startsAt", SECRET);

    assertRejected(bytes(json))
        .hasMessage("Alert property 'startsAt' has an invalid type or format");
  }

  @Test
  void rejectsLabelOfWrongTypeWithoutNamingTheLabel() {
    ObjectNode json = validJson();
    json.putObject("labels").putObject(SECRET).put("nested", true);

    assertRejected(bytes(json)).hasMessage("Alert property 'labels' has an invalid type or format");
  }

  @Test
  void listsEveryMissingRequiredProperty() {
    assertRejected(bytes("{}"))
        .hasMessage(
            "Alert is invalid: alertName: must not be blank; fingerprint: must not be blank;"
                + " source: must not be blank; startsAt: must not be null;"
                + " summary: must not be blank");
  }

  @Test
  void rejectsBlankRequiredProperty() {
    ObjectNode json = validJson();
    json.put("source", "   ");

    assertRejected(bytes(json)).hasMessage("Alert is invalid: source: must not be blank");
  }

  @Test
  void rejectsTooLongProperty() {
    ObjectNode json = validJson();
    json.put("summary", SECRET.repeat(AlertMessage.MAX_SUMMARY_LENGTH));

    assertRejected(bytes(json))
        .hasMessage("Alert is invalid: summary: size must be between 0 and 500");
  }

  @Test
  void rejectsTooLongLabelKeyWithoutNamingIt() {
    ObjectNode json = validJson();
    json.putObject("labels").put(SECRET.repeat(AlertMessage.MAX_LABEL_KEY_LENGTH), "value");

    assertRejected(bytes(json))
        .hasMessage("Alert is invalid: labels.<map key>: size must be between 0 and 100");
  }

  @Test
  void rejectsTooLongLabelValue() {
    ObjectNode json = validJson();
    json.putObject("labels").put("env", SECRET.repeat(AlertMessage.MAX_LABEL_VALUE_LENGTH));

    assertRejected(bytes(json))
        .hasMessage("Alert is invalid: labels.<map value>: size must be between 0 and 500");
  }

  @Test
  void rejectsTooManyLabels() {
    ObjectNode json = validJson();
    ObjectNode labels = json.putObject("labels");
    for (int i = 0; i <= AlertMessage.MAX_LABELS; i++) {
      labels.put("label-" + i, "value");
    }

    assertRejected(bytes(json))
        .hasMessage("Alert is invalid: labels: size must be between 0 and 50");
  }

  @Test
  void rejectsGeneratorUrlThatIsNoUrl() {
    ObjectNode json = validJson();
    json.put("generatorUrl", "not a url " + SECRET);

    assertRejected(bytes(json)).hasMessage("Alert is invalid: generatorUrl: must be a valid URL");
  }

  private AbstractThrowableAssert<?, ? extends Throwable> assertRejected(byte[] payload) {
    return assertThatThrownBy(() -> reader.read(payload))
        .isInstanceOf(InvalidAlertException.class)
        .hasNoCause()
        .hasMessageNotContaining(SECRET);
  }

  private ObjectNode validJson() {
    return objectMapper.valueToTree(WorkflowFixtures.alert());
  }

  private static byte[] bytes(Object json) {
    return json.toString().getBytes(StandardCharsets.UTF_8);
  }
}
