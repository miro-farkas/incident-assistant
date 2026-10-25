package dev.mfa.incident.notification.messaging;

import static dev.mfa.incident.notification.testsupport.IncidentEvents.occurrenceRecorded;
import static dev.mfa.incident.notification.testsupport.IncidentEvents.stateChanged;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import dev.mfa.incident.notification.client.IncidentApiClient;
import dev.mfa.incident.notification.testsupport.IncidentFixtures;
import dev.mfa.incident.notification.testsupport.KafkaTestClient;
import dev.mfa.incident.notification.testsupport.NotificationTables;
import dev.mfa.incident.notification.testsupport.NotificationTables.NotificationRow;
import io.quarkus.mailer.Mail;
import io.quarkus.mailer.MockMailbox;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.ws.rs.ProcessingException;
import java.time.Duration;
import java.util.List;
import java.util.OptionalLong;
import java.util.UUID;
import org.awaitility.core.ConditionTimeoutException;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * End to end inside the service: an event on {@code ops.incidents.events.v1} → recorded → sent by
 * the scheduled sender → mail in the mock mailbox (A-0011, P-0020).
 */
@QuarkusTest
class NotificationFlowTest {

  private static final Duration TIMEOUT = KafkaTestClient.TIMEOUT;
  private static final String APPROVER_1 = "approver-1@example.test";
  private static final String APPROVER_2 = "approver-2@example.test";
  private static final String ON_CALL = "on-call@example.test";
  private static final String UI = "https://incidents.example.test/incidents/";

  private static boolean consumerAssigned;

  @InjectMock @RestClient IncidentApiClient api;

  @Inject KafkaTestClient kafka;
  @Inject NotificationTables tables;
  @Inject MockMailbox mailbox;

  @BeforeEach
  void stubApiAndWaitForTheConsumer() {
    when(api.get(any()))
        .thenReturn(IncidentFixtures.detail("HighErrorRate", "checkout", "critical"));
    // The consumer group starts at the latest offset: send probes until one is processed, so the
    // test's events are not produced before the consumer has its partitions.
    if (!consumerAssigned) {
      await()
          .atMost(TIMEOUT)
          .pollInterval(Duration.ofSeconds(1))
          .until(() -> processedAfterSending(UUID.randomUUID()));
      consumerAssigned = true;
    }
  }

  @Test
  void approvalNeededMailsEveryApproverWithDetailsAndLink() {
    UUID incident = UUID.randomUUID();
    UUID event = UUID.randomUUID();

    kafka.sendEvent(incident, stateChanged(event, incident, 3, "AWAITING_APPROVAL", null));

    for (String approver : List.of(APPROVER_1, APPROVER_2)) {
      Mail mail = awaitMail(approver, incident);
      assertThat(mail.getSubject())
          .isEqualTo("[incident-assistant] Approval needed: checkout (critical)");
      assertThat(mail.getText())
          .contains(incident.toString(), "AWAITING_APPROVAL", "HighErrorRate", UI + incident)
          .contains("checkout (as reported by the alert)")
          .doesNotContain(IncidentFixtures.SUMMARY);
      assertThat(mail.getHtml())
          .contains("href=\"" + UI + incident + "\"")
          .doesNotContain(IncidentFixtures.SUMMARY);
    }
    await()
        .atMost(TIMEOUT)
        .until(
            () ->
                tables.notifications(event).stream().allMatch(row -> row.status().equals("SENT")));
    assertThat(tables.notifications(event))
        .extracting(NotificationRow::recipient, NotificationRow::rule)
        .containsExactly(
            tuple(APPROVER_1, "approval-needed"), tuple(APPROVER_2, "approval-needed"));
  }

  @Test
  void triagedServiceAndSeverityReplaceTheAlertsOwn() {
    UUID incident = UUID.randomUUID();
    when(api.get(eq(incident)))
        .thenReturn(IncidentFixtures.triaged("HighErrorRate", "checkout", "HIGH"));

    kafka.sendEvent(
        incident, stateChanged(UUID.randomUUID(), incident, 3, "AWAITING_APPROVAL", null));

    Mail mail = awaitMail(APPROVER_1, incident);
    assertThat(mail.getSubject())
        .isEqualTo("[incident-assistant] Approval needed: checkout (HIGH)");
    assertThat(mail.getText()).contains("checkout (triaged)").doesNotContain("reported-service");
  }

  @Test
  void escalationMailsOnCallWithTheReason() {
    UUID incident = UUID.randomUUID();

    kafka.sendEvent(
        incident,
        stateChanged(UUID.randomUUID(), incident, 4, "ESCALATED", "DIAGNOSIS_BUDGET_EXCEEDED"));

    Mail mail = awaitMail(ON_CALL, incident);
    assertThat(mail.getSubject())
        .isEqualTo(
            "[incident-assistant] Escalated: checkout (critical) – DIAGNOSIS_BUDGET_EXCEEDED");
    assertThat(mail.getText()).contains("DIAGNOSIS_BUDGET_EXCEEDED", UI + incident);
  }

  @Test
  void duplicateEventSendsOneMailPerRecipient() {
    UUID incident = UUID.randomUUID();
    UUID event = UUID.randomUUID();
    String payload = stateChanged(event, incident, 3, "AWAITING_APPROVAL", null);

    kafka.sendEvent(incident, payload);
    kafka.sendEvent(incident, payload);
    awaitProcessedAfter(incident);

    assertThat(tables.notifications(event)).hasSize(2);
    awaitMail(APPROVER_1, incident);
    assertThat(mailsFor(APPROVER_1, incident)).hasSize(1);
  }

  @Test
  void olderEventOfAnIncidentSendsNothing() {
    UUID incident = UUID.randomUUID();
    UUID older = UUID.randomUUID();

    kafka.sendEvent(incident, stateChanged(UUID.randomUUID(), incident, 5, "ESCALATED", "X"));
    kafka.sendEvent(incident, stateChanged(older, incident, 4, "AWAITING_APPROVAL", null));
    awaitProcessedAfter(incident);

    assertThat(tables.notifications(older)).isEmpty();
    awaitMail(ON_CALL, incident);
    assertThat(mailsFor(APPROVER_1, incident)).isEmpty();
  }

  @Test
  void otherEventTypesAndUnreadableEventsAreSkippedWithoutRecording() {
    UUID incident = UUID.randomUUID();

    // The occurrence event carries version 1 like the state event after it; had it been
    // recorded, the state event would count as already seen and notify nobody.
    kafka.sendEvent(incident, occurrenceRecorded(UUID.randomUUID(), incident, 2));
    kafka.sendEvent(incident, "not json");
    kafka.sendEvent(incident, "{\"eventType\": \"incident.state-changed\", \"state\": \"x\"}");
    kafka.sendEvent(
        incident, stateChanged(UUID.randomUUID(), incident, 1, "AWAITING_APPROVAL", null));

    awaitMail(APPROVER_1, incident);
  }

  @Test
  void stateWithoutRuleWritesNoNotification() {
    UUID incident = UUID.randomUUID();
    UUID event = UUID.randomUUID();

    kafka.sendEvent(incident, stateChanged(event, incident, 1, "TRIAGED", null));

    await().atMost(TIMEOUT).until(() -> tables.cursor(incident).isPresent());
    assertThat(tables.notifications(event)).isEmpty();
  }

  @Test
  void unavailableDetailsSendTheMinimalMessage() {
    UUID incident = UUID.randomUUID();
    when(api.get(eq(incident))).thenThrow(new ProcessingException("connection refused"));

    kafka.sendEvent(
        incident, stateChanged(UUID.randomUUID(), incident, 3, "AWAITING_APPROVAL", null));

    Mail mail = awaitMail(APPROVER_1, incident);
    assertThat(mail.getSubject())
        .isEqualTo("[incident-assistant] Approval needed: unknown (unknown)");
    assertThat(mail.getText())
        .contains("Details are not available", "AWAITING_APPROVAL", UI + incident)
        .doesNotContain("HighErrorRate");
  }

  /** Sends a state event without a rule and tells whether it was recorded. */
  private boolean processedAfterSending(UUID incident) {
    kafka.sendEvent(
        incident, stateChanged(UUID.randomUUID(), incident, Long.MAX_VALUE, "TRIAGED", null));
    try {
      await().atMost(Duration.ofSeconds(3)).until(() -> tables.cursor(incident).isPresent());
      return true;
    } catch (ConditionTimeoutException e) {
      return false;
    }
  }

  /**
   * Waits until a later event of the incident is recorded; events of one incident share a
   * partition, so everything sent before it has been handled.
   */
  private void awaitProcessedAfter(UUID incident) {
    kafka.sendEvent(
        incident, stateChanged(UUID.randomUUID(), incident, Long.MAX_VALUE, "TRIAGED", null));
    await()
        .atMost(TIMEOUT)
        .until(() -> tables.cursor(incident).equals(OptionalLong.of(Long.MAX_VALUE)));
  }

  private Mail awaitMail(String recipient, UUID incident) {
    await().atMost(TIMEOUT).until(() -> !mailsFor(recipient, incident).isEmpty());
    return mailsFor(recipient, incident).getFirst();
  }

  private List<Mail> mailsFor(String recipient, UUID incident) {
    List<Mail> mails = mailbox.getMailsSentTo(recipient);
    return mails == null
        ? List.of()
        : mails.stream().filter(m -> m.getText().contains(incident.toString())).toList();
  }
}
