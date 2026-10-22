package dev.mfa.incident.messaging;

import static dev.mfa.incident.testsupport.KafkaTestClient.TIMEOUT;
import static dev.mfa.incident.testsupport.WorkflowFixtures.newIncident;
import static dev.mfa.incident.testsupport.WorkflowFixtures.succeeded;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import dev.mfa.incident.dto.AlertMessage;
import dev.mfa.incident.dto.IncidentDto;
import dev.mfa.incident.dto.IncidentOccurrenceRecorded;
import dev.mfa.incident.dto.IncidentStateChanged;
import dev.mfa.incident.model.IncidentState;
import dev.mfa.incident.model.OutboxEvent;
import dev.mfa.incident.model.StepType;
import dev.mfa.incident.service.AlertIngestionService;
import dev.mfa.incident.service.IncidentService;
import dev.mfa.incident.testsupport.KafkaTestClient;
import dev.mfa.incident.testsupport.WorkflowFixtures;
import dev.mfa.incident.testsupport.WorkflowTables;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.util.List;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;

/** The scheduled relay publishes to Kafka Dev Services (P-0003, A-0007, A-0012). */
@QuarkusTest
class OutboxRelayKafkaTest {

  private static final String EVENTS = "ops.incidents.events.v1";

  @Inject IncidentService incidents;
  @Inject AlertIngestionService ingestion;
  @Inject KafkaTestClient kafka;
  @Inject WorkflowTables tables;

  @Test
  void publishesEventsOfOneIncidentInOrderKeyedByIncidentId() {
    IncidentDto incident = incidents.create(newIncident());
    incident = step(incident, StepType.TRIAGE, IncidentState.TRIAGED);
    incident = step(incident, StepType.DIAGNOSIS, IncidentState.DIAGNOSED);
    incident = step(incident, StepType.PROPOSAL, IncidentState.AWAITING_APPROVAL);
    UUID incidentId = incident.id();
    String key = incidentId.toString();
    List<OutboxEvent> written = tables.outboxEvents(incidentId);

    List<ConsumerRecord<String, String>> received =
        kafka.awaitRecords(EVENTS, r -> key.equals(r.key()), written.size());

    assertThat(received)
        .extracting(ConsumerRecord::value)
        .containsExactlyElementsOf(written.stream().map(OutboxEvent::getPayload).toList());
    assertThat(received)
        .extracting(ConsumerRecord::partition)
        .containsOnly(received.getFirst().partition());
    await()
        .atMost(TIMEOUT)
        .until(
            () ->
                tables.outboxEvents(incidentId).stream().allMatch(e -> e.getPublishedAt() != null));
  }

  @Test
  void publishesOccurrenceEventsInOrderWithTheIncidentsStateEvents() {
    AlertMessage alert = WorkflowFixtures.alert();
    UUID incidentId = ingestion.ingest(alert).incidentId();
    ingestion.ingest(alert);
    IncidentDto incident = incidents.get(incidentId);
    step(incident, StepType.TRIAGE, IncidentState.TRIAGED);
    String key = incidentId.toString();
    List<OutboxEvent> written = tables.outboxEvents(incidentId);
    assertThat(written)
        .extracting(OutboxEvent::getEventType)
        .containsExactly(
            IncidentStateChanged.EVENT_TYPE,
            IncidentOccurrenceRecorded.EVENT_TYPE,
            IncidentStateChanged.EVENT_TYPE);

    List<ConsumerRecord<String, String>> received =
        kafka.awaitRecords(EVENTS, r -> key.equals(r.key()), written.size());

    assertThat(received)
        .extracting(ConsumerRecord::value)
        .containsExactlyElementsOf(written.stream().map(OutboxEvent::getPayload).toList());
    assertThat(received)
        .extracting(ConsumerRecord::partition)
        .containsOnly(received.getFirst().partition());
  }

  private IncidentDto step(IncidentDto incident, StepType step, IncidentState target) {
    return incidents.recordStep(incident.id(), incident.version(), succeeded(step), target, null);
  }
}
