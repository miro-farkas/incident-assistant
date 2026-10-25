package dev.mfa.incident.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.mfa.incident.notification.channel.EmailChannel;
import dev.mfa.incident.notification.client.IncidentApiClient;
import dev.mfa.incident.notification.dto.IncidentStateEventDto;
import dev.mfa.incident.notification.dto.PendingNotificationDto;
import dev.mfa.incident.notification.model.ChannelType;
import dev.mfa.incident.notification.model.Notification;
import dev.mfa.incident.notification.repository.NotificationRepository;
import dev.mfa.incident.notification.testsupport.IncidentFixtures;
import dev.mfa.incident.notification.testsupport.ManualSenderProfile;
import dev.mfa.incident.notification.testsupport.NotificationTables;
import dev.mfa.incident.notification.testsupport.NotificationTables.NotificationRow;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * The sender (P-0020) driven by hand: retries with backoff, the final {@code FAILED}, and disjoint
 * claims of concurrent senders. Configured with 3 attempts and a backoff base of 200 ms.
 */
@QuarkusTest
@TestProfile(ManualSenderProfile.class)
class NotificationSenderTest {

  private static final Duration BACKOFF_BASE = Duration.ofMillis(200);

  @InjectMock EmailChannel email;
  @InjectMock @RestClient IncidentApiClient api;

  @Inject NotificationSender sender;
  @Inject DeliveryRecorder deliveries;
  @Inject NotificationRepository notifications;
  @Inject EventIngestionService ingestion;
  @Inject NotificationTables tables;

  @BeforeEach
  void setUp() {
    tables.clearPending();
    when(email.type()).thenReturn(ChannelType.EMAIL);
    when(api.get(any()))
        .thenReturn(IncidentFixtures.detail("HighErrorRate", "checkout", "critical"));
  }

  @Test
  void acceptedMessageIsMarkedSent() {
    UUID event = escalated();

    assertThat(sender.sendDue()).isEqualTo(1);

    assertThat(tables.notifications(event))
        .singleElement()
        .satisfies(
            row -> {
              assertThat(row.status()).isEqualTo("SENT");
              assertThat(row.attempts()).isEqualTo(1);
            });
  }

  @Test
  void refusedMessageIsRetriedWithBackoffThenFailed() {
    doThrow(new IllegalStateException("SMTP 421 service not available")).when(email).send(any());
    UUID event = escalated();

    assertThat(sender.sendDue()).isEqualTo(1);
    assertThat(row(event).status()).isEqualTo("PENDING");
    assertThat(row(event).attempts()).isEqualTo(1);
    assertThat(row(event).lastError()).contains("SMTP 421");
    assertThat(sender.sendDue()).as("not due before the backoff").isZero();

    await().atMost(Duration.ofSeconds(5)).until(() -> sender.sendDue() == 1);
    assertThat(row(event).attempts()).isEqualTo(2);
    assertThat(sender.sendDue()).as("second backoff is twice the first").isZero();
    await()
        .pollDelay(BACKOFF_BASE)
        .atMost(Duration.ofSeconds(5))
        .until(() -> sender.sendDue() == 1);

    assertThat(row(event).status()).isEqualTo("FAILED");
    assertThat(row(event).attempts()).isEqualTo(3);
    verify(email, times(3)).send(any());
    assertThat(sender.sendDue()).as("a failed notification is not claimed again").isZero();
  }

  @Test
  void concurrentSendersClaimDisjointNotifications() throws Exception {
    approvalNeeded();
    approvalNeeded();
    approvalNeeded();
    CountDownLatch locked = new CountDownLatch(1);
    CountDownLatch release = new CountDownLatch(1);
    CompletableFuture<List<UUID>> other =
        CompletableFuture.supplyAsync(
            () ->
                QuarkusTransaction.requiringNew()
                    .call(
                        () -> {
                          List<UUID> ids =
                              notifications.lockDue(Instant.now(), 4).stream()
                                  .map(Notification::getId)
                                  .toList();
                          locked.countDown();
                          release.await(10, TimeUnit.SECONDS);
                          return ids;
                        }));
    assertThat(locked.await(10, TimeUnit.SECONDS)).isTrue();

    List<UUID> claimed;
    try {
      claimed =
          CompletableFuture.supplyAsync(deliveries::claimDue).get(5, TimeUnit.SECONDS).stream()
              .map(PendingNotificationDto::id)
              .toList();
    } finally {
      release.countDown();
    }

    List<UUID> lockedByOther = other.get(10, TimeUnit.SECONDS);
    assertThat(lockedByOther).hasSize(4);
    assertThat(claimed).hasSize(2).doesNotContainAnyElementsOf(lockedByOther);
  }

  private NotificationRow row(UUID event) {
    return tables.notifications(event).getFirst();
  }

  /** One pending notification for on-call. */
  private UUID escalated() {
    return ingest("ESCALATED", "DIAGNOSIS_FAILED");
  }

  /** Two pending notifications, one per approver. */
  private UUID approvalNeeded() {
    return ingest("AWAITING_APPROVAL", null);
  }

  private UUID ingest(String state, String reasonCode) {
    IncidentStateEventDto event =
        new IncidentStateEventDto(
            UUID.randomUUID(), Instant.now(), UUID.randomUUID(), 1, state, reasonCode);
    ingestion.ingest(event);
    return event.eventId();
  }
}
