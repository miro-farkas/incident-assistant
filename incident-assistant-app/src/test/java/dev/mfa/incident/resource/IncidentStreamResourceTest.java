package dev.mfa.incident.resource;

import static dev.mfa.incident.testsupport.WorkflowFixtures.newIncident;
import static dev.mfa.incident.testsupport.WorkflowFixtures.succeeded;
import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.mfa.incident.dto.AlertMessage;
import dev.mfa.incident.dto.IncidentDto;
import dev.mfa.incident.dto.IncidentOccurrenceRecorded;
import dev.mfa.incident.dto.IncidentStateChanged;
import dev.mfa.incident.model.IncidentState;
import dev.mfa.incident.model.StepType;
import dev.mfa.incident.service.AlertIngestionService;
import dev.mfa.incident.service.IncidentService;
import dev.mfa.incident.service.IncidentStreamService;
import dev.mfa.incident.testsupport.SseTestClient;
import dev.mfa.incident.testsupport.SseTestClient.Event;
import dev.mfa.incident.testsupport.WorkflowFixtures;
import io.quarkus.test.common.http.TestHTTPResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import jakarta.inject.Inject;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** The stream carries topic events end to end: service → outbox → Kafka → consumer → SSE. */
@QuarkusTest
class IncidentStreamResourceTest {

  private static final Duration TIMEOUT = Duration.ofSeconds(30);
  private static final String STREAM = "/api/v1/incidents/stream";
  private static final String STATE_CHANGED = IncidentStateChanged.EVENT_TYPE;
  private static final String OCCURRENCE_RECORDED = IncidentOccurrenceRecorded.EVENT_TYPE;

  @TestHTTPResource(STREAM)
  URI streamUri;

  @Inject IncidentService incidents;
  @Inject AlertIngestionService ingestion;
  @Inject IncidentStreamService streams;
  @Inject ObjectMapper objectMapper;

  @Test
  @TestSecurity(user = "viewer-subject", roles = "incident-viewer")
  void everyOpenStreamReceivesTheEventsOfAnIncidentInOrder() throws Exception {
    int before = streams.openStreams();
    try (SseTestClient first = SseTestClient.open(streamUri);
        SseTestClient second = SseTestClient.open(streamUri)) {
      assertThat(first.status(TIMEOUT)).isEqualTo(200);
      assertThat(second.status(TIMEOUT)).isEqualTo(200);
      await().atMost(TIMEOUT).until(() -> streams.openStreams() == before + 2);

      IncidentDto created = incidents.create(newIncident());
      IncidentDto triaged =
          incidents.recordStep(
              created.id(),
              created.version(),
              succeeded(StepType.TRIAGE),
              IncidentState.TRIAGED,
              null);

      for (SseTestClient client : List.of(first, second)) {
        List<JsonNode> received =
            List.of(
                next(client, created.id(), STATE_CHANGED),
                next(client, created.id(), STATE_CHANGED));
        assertThat(received)
            .extracting(e -> e.get("state").asText())
            .containsExactly("RECEIVED", "TRIAGED");
        assertThat(received.getLast().get("incidentVersion").asLong()).isEqualTo(triaged.version());
      }
    }
    await().atMost(TIMEOUT).until(() -> streams.openStreams() == before);
  }

  @Test
  @TestSecurity(user = "viewer-subject", roles = "incident-viewer")
  void streamCarriesOccurrenceEventsWithTheirOwnEventName() throws Exception {
    int before = streams.openStreams();
    try (SseTestClient client = SseTestClient.open(streamUri)) {
      assertThat(client.status(TIMEOUT)).isEqualTo(200);
      await().atMost(TIMEOUT).until(() -> streams.openStreams() == before + 1);

      AlertMessage alert = WorkflowFixtures.alert();
      UUID incidentId = ingestion.ingest(alert).incidentId();
      ingestion.ingest(alert);

      assertThat(next(client, incidentId, STATE_CHANGED).get("state").asText())
          .isEqualTo("RECEIVED");
      JsonNode occurrence = next(client, incidentId, OCCURRENCE_RECORDED);
      assertThat(occurrence.get("occurrenceCount").asLong()).isEqualTo(1);
    }
    await().atMost(TIMEOUT).until(() -> streams.openStreams() == before);
  }

  @Test
  void streamRequiresAuthentication() {
    given().accept("text/event-stream").when().get(STREAM).then().statusCode(401);
  }

  @Test
  @TestSecurity(user = "someone", roles = "knowledge-admin")
  void streamRequiresAnIncidentRole() {
    given()
        .accept("text/event-stream")
        .when()
        .get(STREAM)
        .then()
        .statusCode(403)
        .contentType(ProblemDetail.MEDIA_TYPE);
  }

  /** The next event of the incident, checked for SSE framing, and its parsed payload. */
  private JsonNode next(SseTestClient client, UUID incidentId, String eventName) throws Exception {
    Event event = client.next(e -> e.data().contains(incidentId.toString()), TIMEOUT);
    JsonNode payload = objectMapper.readTree(event.data());
    assertThat(event.name()).isEqualTo(eventName);
    assertThat(payload.get("eventType").asText()).isEqualTo(eventName);
    assertThat(event.otherFields()).as("fields besides event, id and data").isEmpty();
    assertThat(event.id()).isEqualTo(payload.get("eventId").asText());
    assertThat(payload.get("incidentId").asText()).isEqualTo(incidentId.toString());
    return payload;
  }
}
