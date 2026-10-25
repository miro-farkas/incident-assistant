package dev.mfa.incident.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import dev.mfa.incident.notification.dto.EventRecordingResult;
import dev.mfa.incident.notification.dto.IncidentStateEventDto;
import dev.mfa.incident.notification.testsupport.ManualSenderProfile;
import dev.mfa.incident.notification.testsupport.NotificationTables;
import dev.mfa.incident.notification.testsupport.NotificationTables.NotificationRow;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;

/** Recording events into pending notifications (P-0020), without the scheduled sender. */
@QuarkusTest
@TestProfile(ManualSenderProfile.class)
class EventRecorderTest {

  @Inject EventIngestionService ingestion;
  @Inject NotificationTables tables;

  @Test
  void matchingEventWritesOnePendingNotificationPerRecipient() {
    IncidentStateEventDto event = event(UUID.randomUUID(), 3, "AWAITING_APPROVAL");

    assertThat(ingestion.ingest(event)).isEqualTo(new EventRecordingResult.Recorded(2));

    assertThat(tables.notifications(event.eventId()))
        .extracting(NotificationRow::recipient, NotificationRow::status, NotificationRow::attempts)
        .containsExactly(
            tuple("approver-1@example.test", "PENDING", 0),
            tuple("approver-2@example.test", "PENDING", 0));
  }

  @Test
  void redeliveredEventChangesNothing() {
    IncidentStateEventDto event = event(UUID.randomUUID(), 3, "AWAITING_APPROVAL");
    ingestion.ingest(event);

    assertThat(ingestion.ingest(event)).isEqualTo(new EventRecordingResult.Outdated(3));
    assertThat(tables.notifications(event.eventId())).hasSize(2);
  }

  @Test
  void olderEventNotifiesNobody() {
    UUID incident = UUID.randomUUID();
    ingestion.ingest(event(incident, 5, "TRIAGED"));
    IncidentStateEventDto older = event(incident, 4, "AWAITING_APPROVAL");

    assertThat(ingestion.ingest(older)).isEqualTo(new EventRecordingResult.Outdated(5));
    assertThat(tables.cursor(incident)).hasValue(5);
    assertThat(tables.notifications(older.eventId())).isEmpty();
  }

  @Test
  void newerEventAfterAnOlderOneNotifies() {
    UUID incident = UUID.randomUUID();
    ingestion.ingest(event(incident, 1, "RECEIVED"));

    assertThat(ingestion.ingest(event(incident, 2, "ESCALATED")))
        .isEqualTo(new EventRecordingResult.Recorded(1));
  }

  @Test
  void concurrentDeliveriesOfOneEventRecordItOnce() throws Exception {
    IncidentStateEventDto event = event(UUID.randomUUID(), 3, "AWAITING_APPROVAL");
    Callable<EventRecordingResult> ingest = () -> ingestion.ingest(event);

    List<EventRecordingResult> results;
    try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
      List<Future<EventRecordingResult>> futures = executor.invokeAll(List.of(ingest, ingest));
      results = List.of(futures.get(0).get(), futures.get(1).get());
    }

    assertThat(results)
        .containsExactlyInAnyOrder(
            new EventRecordingResult.Recorded(2), new EventRecordingResult.Outdated(3));
    assertThat(tables.notifications(event.eventId())).hasSize(2);
  }

  private static IncidentStateEventDto event(UUID incident, long version, String state) {
    return new IncidentStateEventDto(
        UUID.randomUUID(),
        Instant.parse("2026-10-18T10:05:00Z"),
        incident,
        version,
        state,
        "ESCALATED".equals(state) ? "DIAGNOSIS_FAILED" : null);
  }
}
