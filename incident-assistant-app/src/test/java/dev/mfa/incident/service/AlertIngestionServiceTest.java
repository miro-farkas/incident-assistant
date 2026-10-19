package dev.mfa.incident.service;

import static dev.mfa.incident.testsupport.WorkflowFixtures.alert;
import static dev.mfa.incident.testsupport.WorkflowFixtures.failed;
import static dev.mfa.incident.testsupport.WorkflowFixtures.succeeded;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.mfa.incident.dto.AlertIngestionResult;
import dev.mfa.incident.dto.AlertIngestionResult.IncidentOpened;
import dev.mfa.incident.dto.AlertIngestionResult.OccurrenceRecorded;
import dev.mfa.incident.dto.AlertMessage;
import dev.mfa.incident.dto.IncidentDto;
import dev.mfa.incident.dto.ReasonCode;
import dev.mfa.incident.model.IncidentState;
import dev.mfa.incident.model.StepType;
import dev.mfa.incident.testsupport.WorkflowTables;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

@QuarkusTest
class AlertIngestionServiceTest {

  private static final int CONCURRENT_ALERTS = 8;
  private static final long CONCURRENCY_TIMEOUT_SECONDS = 30;

  @Inject AlertIngestionService ingestion;
  @Inject IncidentService incidents;
  @Inject WorkflowTables tables;
  @Inject ObjectMapper objectMapper;

  @Test
  void opensReceivedIncidentForNewAlert() {
    AlertMessage alert = alert();

    AlertIngestionResult result = ingestion.ingest(alert);

    IncidentDto incident = opened(result);
    assertThat(incident.state()).isEqualTo(IncidentState.RECEIVED);
    assertThat(incident.source()).isEqualTo(alert.source());
    assertThat(incident.fingerprint()).isEqualTo(alert.fingerprint());
    assertThat(json(tables.alertPayload(incident.id()))).isEqualTo(objectMapper.valueToTree(alert));
    assertThat(tables.outboxEvents(incident.id())).hasSize(1);
    assertThat(tables.occurrencePayloads(incident.id())).isEmpty();
  }

  @Test
  void recordsDuplicateAsOccurrenceWithoutChangingTheIncident() {
    AlertMessage alert = alert();
    IncidentDto incident = opened(ingestion.ingest(alert));

    AlertIngestionResult duplicate = ingestion.ingest(alert);

    assertThat(duplicate).isInstanceOf(OccurrenceRecorded.class);
    OccurrenceRecorded occurrence = (OccurrenceRecorded) duplicate;
    assertThat(occurrence.incidentId()).isEqualTo(incident.id());
    assertThat(occurrence.occurrenceId()).isNotNull();
    assertThat(incidents.get(incident.id())).isEqualTo(incident);
    assertThat(tables.occurrencePayloads(incident.id()))
        .singleElement()
        .satisfies(p -> assertThat(json(p)).isEqualTo(objectMapper.valueToTree(alert)));
    assertThat(tables.outboxEvents(incident.id())).hasSize(1);
    assertThat(tables.incidentIds(alert.source(), alert.fingerprint()))
        .containsExactly(incident.id());
  }

  @Test
  void occurrenceDoesNotConflictWithStepInProgress() {
    AlertMessage alert = alert();
    IncidentDto incident = opened(ingestion.ingest(alert));

    ingestion.ingest(alert);
    IncidentDto triaged =
        incidents.recordStep(
            incident.id(),
            incident.version(),
            succeeded(StepType.TRIAGE),
            IncidentState.TRIAGED,
            null);

    assertThat(triaged.state()).isEqualTo(IncidentState.TRIAGED);
  }

  @Test
  void opensNewIncidentWhenPreviousOneIsClosed() {
    AlertMessage alert = alert();
    IncidentDto first = opened(ingestion.ingest(alert));
    incidents.recordStep(
        first.id(),
        first.version(),
        failed(StepType.TRIAGE, "Model unavailable"),
        IncidentState.ESCALATED,
        new ReasonCode("TRIAGE_FAILED"));

    IncidentDto second = opened(ingestion.ingest(alert));

    assertThat(second.id()).isNotEqualTo(first.id());
    assertThat(tables.incidentIds(alert.source(), alert.fingerprint()))
        .containsExactly(first.id(), second.id());
    assertThat(tables.occurrencePayloads(first.id())).isEmpty();
  }

  /**
   * Identical alerts ingested at the same moment race on the partial unique index; the losers must
   * end up as occurrences of the winner's incident.
   */
  @Test
  void concurrentIdenticalAlertsOpenOneIncident() throws Exception {
    AlertMessage alert = alert();
    CountDownLatch start = new CountDownLatch(1);
    Callable<AlertIngestionResult> ingest =
        () -> {
          start.await();
          return ingestion.ingest(alert);
        };

    List<AlertIngestionResult> results = new ArrayList<>();
    try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
      List<Future<AlertIngestionResult>> futures = new ArrayList<>();
      for (int i = 0; i < CONCURRENT_ALERTS; i++) {
        futures.add(executor.submit(ingest));
      }
      start.countDown();
      for (Future<AlertIngestionResult> future : futures) {
        results.add(future.get(CONCURRENCY_TIMEOUT_SECONDS, TimeUnit.SECONDS));
      }
    }

    List<IncidentOpened> opened =
        results.stream()
            .filter(IncidentOpened.class::isInstance)
            .map(IncidentOpened.class::cast)
            .toList();
    assertThat(opened).hasSize(1);
    assertThat(results)
        .extracting(AlertIngestionResult::incidentId)
        .containsOnly(opened.getFirst().incidentId());
    assertThat(tables.incidentIds(alert.source(), alert.fingerprint()))
        .containsExactly(opened.getFirst().incidentId());
    assertThat(tables.occurrencePayloads(opened.getFirst().incidentId()))
        .hasSize(CONCURRENT_ALERTS - 1);
  }

  private static IncidentDto opened(AlertIngestionResult result) {
    assertThat(result).isInstanceOf(IncidentOpened.class);
    return ((IncidentOpened) result).incident();
  }

  private JsonNode json(String text) {
    try {
      return objectMapper.readTree(text);
    } catch (JsonProcessingException e) {
      throw new IllegalArgumentException(e);
    }
  }
}
