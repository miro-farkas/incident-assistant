package dev.mfa.incident.messaging;

import static dev.mfa.incident.testsupport.KafkaTestClient.TIMEOUT;
import static dev.mfa.incident.testsupport.KafkaTestClient.header;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.mfa.incident.config.AlertIngestionConfig;
import dev.mfa.incident.dto.AlertMessage;
import dev.mfa.incident.dto.IncidentStateChanged;
import dev.mfa.incident.service.IncidentService;
import dev.mfa.incident.testsupport.IncidentEventSchema;
import dev.mfa.incident.testsupport.KafkaTestClient;
import dev.mfa.incident.testsupport.WorkflowFixtures;
import dev.mfa.incident.testsupport.WorkflowTables;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;

/** Alerts through Kafka Dev Services: consumer, ingestion, relay and dead-letter topic (A-0003). */
@QuarkusTest
class AlertIngestionKafkaTest {

  private static final String ALERTS = "ops.alerts.v1";
  private static final String DLQ = "ops.alerts.v1.dlq";
  private static final String EVENTS = "ops.incidents.events.v1";
  private static final String REASON = "dead-letter-reason";

  /** How long no incident may appear for a rejected alert. */
  private static final Duration SETTLE = Duration.ofSeconds(2);

  @Inject KafkaTestClient kafka;
  @Inject WorkflowTables tables;
  @Inject IncidentService incidents;
  @Inject ObjectMapper objectMapper;
  @Inject AlertIngestionConfig config;

  @Test
  void alertOpensIncidentAndPublishesCreationEvent() throws Exception {
    AlertMessage alert = WorkflowFixtures.alert();

    kafka.send(ALERTS, alert.fingerprint(), objectMapper.writeValueAsString(alert));

    UUID incidentId = awaitIncident(alert);
    assertThat(incidents.get(incidentId).state().name()).isEqualTo("RECEIVED");
    ConsumerRecord<String, String> event =
        kafka.awaitRecords(EVENTS, r -> incidentId.toString().equals(r.key()), 1).getFirst();
    JsonNode payload = objectMapper.readTree(event.value());
    IncidentEventSchema.load(objectMapper).assertConforms(payload);
    assertThat(payload.get("eventType").asText()).isEqualTo(IncidentStateChanged.EVENT_TYPE);
    assertThat(payload.get("incidentId").asText()).isEqualTo(incidentId.toString());
    assertThat(payload.get("state").asText()).isEqualTo("RECEIVED");
    assertThat(payload.get("incidentVersion").asLong()).isZero();
  }

  @Test
  void duplicateAlertIsRecordedAsOccurrence() throws Exception {
    AlertMessage alert = WorkflowFixtures.alert();
    String value = objectMapper.writeValueAsString(alert);

    kafka.send(ALERTS, alert.fingerprint(), value);
    UUID incidentId = awaitIncident(alert);
    kafka.send(ALERTS, alert.fingerprint(), value);

    await().atMost(TIMEOUT).until(() -> tables.occurrencePayloads(incidentId).size() == 1);
    assertThat(tables.incidentIds(alert.source(), alert.fingerprint())).containsExactly(incidentId);
    // Creation event and occurrence event (A-0012).
    assertThat(tables.outboxEvents(incidentId)).hasSize(2);
  }

  @Test
  void malformedJsonGoesToDeadLetterTopic() {
    String key = "malformed-" + UUID.randomUUID();

    kafka.send(ALERTS, key, "{\"source\": \"prometheus\", \"fingerprint\": " + key);

    ConsumerRecord<String, String> dead = awaitDeadLetter(key);
    assertThat(dead.value()).isEqualTo("{\"source\": \"prometheus\", \"fingerprint\": " + key);
    assertThat(header(dead, REASON)).startsWith("Alert payload is not valid JSON (line 1, column");
    assertThat(header(dead, REASON)).doesNotContain(key);
  }

  @Test
  void alertMissingRequiredPropertyGoesToDeadLetterTopic() throws Exception {
    AlertMessage alert = WorkflowFixtures.alert();
    ObjectNode json = objectMapper.valueToTree(alert);
    json.remove("summary");
    String key = "missing-" + alert.fingerprint();

    kafka.send(ALERTS, key, json.toString());

    ConsumerRecord<String, String> dead = awaitDeadLetter(key);
    assertThat(objectMapper.readTree(dead.value())).isEqualTo(json);
    assertThat(header(dead, REASON)).isEqualTo("Alert is invalid: summary: must not be blank");
    await()
        .during(SETTLE)
        .atMost(TIMEOUT)
        .until(() -> tables.incidentIds(alert.source(), alert.fingerprint()).isEmpty());
  }

  @Test
  void oversizedAlertGoesToDeadLetterTopic() {
    String key = "oversized-" + UUID.randomUUID();
    String value = "x".repeat(config.maxPayloadBytes() + 1);

    kafka.send(ALERTS, key, value);

    ConsumerRecord<String, String> dead = awaitDeadLetter(key);
    assertThat(dead.value()).hasSize(value.length());
    assertThat(header(dead, REASON))
        .isEqualTo(
            "Alert payload has " + value.length() + " bytes, limit is " + config.maxPayloadBytes());
  }

  private UUID awaitIncident(AlertMessage alert) {
    await()
        .atMost(TIMEOUT)
        .until(() -> !tables.incidentIds(alert.source(), alert.fingerprint()).isEmpty());
    List<UUID> ids = tables.incidentIds(alert.source(), alert.fingerprint());
    assertThat(ids).hasSize(1);
    return ids.getFirst();
  }

  private ConsumerRecord<String, String> awaitDeadLetter(String key) {
    return kafka.awaitRecords(DLQ, r -> key.equals(r.key()), 1).getFirst();
  }
}
