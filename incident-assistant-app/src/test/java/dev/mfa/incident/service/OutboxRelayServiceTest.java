package dev.mfa.incident.service;

import static dev.mfa.incident.testsupport.WorkflowFixtures.newIncident;
import static dev.mfa.incident.testsupport.WorkflowFixtures.succeeded;
import static org.assertj.core.api.Assertions.assertThat;

import dev.mfa.incident.dto.IncidentDto;
import dev.mfa.incident.dto.OutboxRecord;
import dev.mfa.incident.model.IncidentState;
import dev.mfa.incident.model.OutboxEvent;
import dev.mfa.incident.model.StepType;
import dev.mfa.incident.repository.OutboxEventRepository;
import dev.mfa.incident.testsupport.RecordingOutboxPublisher;
import dev.mfa.incident.testsupport.WorkflowTables;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * The relay against a recording publisher, with the scheduler off so each test drives the relay
 * itself. Kafka delivery is covered by {@code OutboxRelayKafkaTest}.
 */
@QuarkusTest
@TestProfile(OutboxRelayServiceTest.ManualRelay.class)
class OutboxRelayServiceTest {

  private static final int BATCH_SIZE = 2;
  private static final long LOCK_TIMEOUT_SECONDS = 30;

  public static final class ManualRelay implements QuarkusTestProfile {

    @Override
    public Map<String, String> getConfigOverrides() {
      return Map.of(
          "quarkus.scheduler.enabled",
          "false",
          "incident.outbox.relay.batch-size",
          String.valueOf(BATCH_SIZE));
    }

    @Override
    public Set<Class<?>> getEnabledAlternatives() {
      return Set.of(RecordingOutboxPublisher.class);
    }
  }

  @Inject OutboxRelayService relay;
  @Inject IncidentService incidents;
  @Inject OutboxEventRepository outboxEvents;
  @Inject RecordingOutboxPublisher publisher;
  @Inject WorkflowTables tables;

  @BeforeEach
  void publishLeftoversAndReset() {
    relay.relayPending();
    publisher.reset();
  }

  @Test
  void publishesEventsOfAnIncidentInOrderAndMarksThem() {
    IncidentDto incident = incidentWithThreeEvents();
    List<OutboxEvent> written = tables.outboxEvents(incident.id());

    int published = relay.relayPending();

    assertThat(published).isEqualTo(written.size());
    List<OutboxRecord> records = publisher.published(incident.id().toString());
    assertThat(records)
        .extracting(OutboxRecord::eventId)
        .containsExactlyElementsOf(written.stream().map(OutboxEvent::getEventId).toList());
    assertThat(records)
        .extracting(OutboxRecord::payload)
        .containsExactlyElementsOf(written.stream().map(OutboxEvent::getPayload).toList());
    assertThat(tables.outboxEvents(incident.id()))
        .allSatisfy(e -> assertThat(e.getPublishedAt()).isNotNull());
    assertThat(relay.relayPending()).isZero();
  }

  @Test
  void drainsMoreThanOneBatchPerRun() {
    List<IncidentDto> created = new ArrayList<>();
    for (int i = 0; i < 2 * BATCH_SIZE + 1; i++) {
      created.add(incidents.create(newIncident()));
    }

    assertThat(relay.relayPending()).isEqualTo(created.size());
    for (IncidentDto incident : created) {
      assertThat(publisher.published(incident.id().toString())).hasSize(1);
    }
  }

  @Test
  void stopsAtFailedSendAndResumesInOrder() {
    IncidentDto incident = incidentWithThreeEvents();
    List<OutboxEvent> written = tables.outboxEvents(incident.id());
    publisher.failOn(written.get(1).getEventId());

    assertThat(relay.relayPending()).isEqualTo(1);
    assertThat(tables.outboxEvents(incident.id()))
        .extracting(e -> e.getPublishedAt() != null)
        .containsExactly(true, false, false);

    publisher.reset();
    assertThat(relay.relayPending()).isEqualTo(2);
    assertThat(publisher.published(incident.id().toString()))
        .extracting(OutboxRecord::eventId)
        .containsExactly(written.get(1).getEventId(), written.get(2).getEventId());
    assertThat(tables.outboxEvents(incident.id()))
        .allSatisfy(e -> assertThat(e.getPublishedAt()).isNotNull());
  }

  @Test
  void skipsWhileAnotherInstanceHoldsTheRelayLock() throws Exception {
    IncidentDto incident = incidents.create(newIncident());
    CountDownLatch locked = new CountDownLatch(1);
    CountDownLatch release = new CountDownLatch(1);

    try (ExecutorService otherInstance = Executors.newVirtualThreadPerTaskExecutor()) {
      Future<Boolean> lock =
          otherInstance.submit(
              () ->
                  QuarkusTransaction.requiringNew()
                      .call(
                          () -> {
                            boolean acquired = outboxEvents.tryLockRelay();
                            locked.countDown();
                            release.await(LOCK_TIMEOUT_SECONDS, TimeUnit.SECONDS);
                            return acquired;
                          }));
      assertThat(locked.await(LOCK_TIMEOUT_SECONDS, TimeUnit.SECONDS)).isTrue();

      assertThat(relay.relayPending()).isZero();
      assertThat(publisher.published(incident.id().toString())).isEmpty();

      release.countDown();
      assertThat(lock.get(LOCK_TIMEOUT_SECONDS, TimeUnit.SECONDS)).isTrue();
    }

    assertThat(relay.relayPending()).isEqualTo(1);
    assertThat(publisher.published(incident.id().toString())).hasSize(1);
  }

  private IncidentDto incidentWithThreeEvents() {
    IncidentDto incident = incidents.create(newIncident());
    incident =
        incidents.recordStep(
            incident.id(),
            incident.version(),
            succeeded(StepType.TRIAGE),
            IncidentState.TRIAGED,
            null);
    return incidents.recordStep(
        incident.id(),
        incident.version(),
        succeeded(StepType.DIAGNOSIS),
        IncidentState.DIAGNOSED,
        null);
  }
}
