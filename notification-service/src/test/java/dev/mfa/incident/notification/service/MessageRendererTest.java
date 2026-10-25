package dev.mfa.incident.notification.service;

import static org.assertj.core.api.Assertions.assertThat;

import dev.mfa.incident.notification.dto.IncidentDetailsDto;
import dev.mfa.incident.notification.dto.NotificationMessageDto;
import dev.mfa.incident.notification.dto.PendingNotificationDto;
import dev.mfa.incident.notification.model.ChannelType;
import dev.mfa.incident.notification.testsupport.ManualSenderProfile;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Message content of A-0011; alert values are untrusted text. */
@QuarkusTest
@TestProfile(ManualSenderProfile.class)
class MessageRendererTest {

  private static final UUID INCIDENT = UUID.fromString("0192a3f0-0000-7000-8000-000000000042");

  @Inject MessageRenderer renderer;

  @Test
  void showsTheFieldsOfA0011AndLinksToTheConfiguredUi() {
    NotificationMessageDto message =
        renderer.render(
            pending("escalated", "ESCALATED", "DIAGNOSIS_BUDGET_EXCEEDED"),
            Optional.of(new IncidentDetailsDto("HighErrorRate", "checkout", "critical", true)));

    assertThat(message.recipient()).isEqualTo("on-call@example.test");
    assertThat(message.subject())
        .isEqualTo(
            "[incident-assistant] Escalated: checkout (critical) – DIAGNOSIS_BUDGET_EXCEEDED");
    assertThat(message.text())
        .contains(
            INCIDENT.toString(),
            "ESCALATED",
            "DIAGNOSIS_BUDGET_EXCEEDED",
            "2026-10-18T10:05:00Z",
            "HighErrorRate",
            "https://incidents.example.test/incidents/" + INCIDENT);
    assertThat(message.html())
        .contains("href=\"https://incidents.example.test/incidents/" + INCIDENT + "\"");
  }

  @Test
  void escapesHtmlAndKeepsAlertValuesOnOneLine() {
    NotificationMessageDto message =
        renderer.render(
            pending("approval-needed", "AWAITING_APPROVAL", null),
            Optional.of(
                new IncidentDetailsDto(
                    "<script>alert(1)</script>",
                    "checkout\r\nBcc: attacker@example.test",
                    "x".repeat(500),
                    false)));

    assertThat(message.subject())
        .doesNotContain("\r", "\n")
        .startsWith("[incident-assistant] Approval needed: checkout Bcc: attacker@example.test (");
    assertThat(message.subject().length()).isLessThan(2 * MessageRenderer.MAX_VALUE_LENGTH + 60);
    assertThat(message.html())
        .doesNotContain("<script>")
        .contains("&lt;script&gt;alert(1)&lt;/script&gt;");
    assertThat(message.text()).doesNotContain("\nBcc:");
  }

  @Test
  void minimalMessageWithoutDetails() {
    NotificationMessageDto message =
        renderer.render(pending("approval-needed", "AWAITING_APPROVAL", null), Optional.empty());

    assertThat(message.subject())
        .isEqualTo("[incident-assistant] Approval needed: unknown (unknown)");
    assertThat(message.text())
        .contains(
            INCIDENT.toString(),
            "AWAITING_APPROVAL",
            "Details are not available",
            "https://incidents.example.test/incidents/" + INCIDENT)
        .doesNotContain("Reason:");
  }

  private static PendingNotificationDto pending(String rule, String state, String reasonCode) {
    return new PendingNotificationDto(
        UUID.randomUUID(),
        INCIDENT,
        rule,
        state,
        reasonCode,
        Instant.parse("2026-10-18T10:05:00.123456Z"),
        ChannelType.EMAIL,
        "ESCALATED".equals(state) ? "on-call@example.test" : "approver-1@example.test",
        1);
  }
}
