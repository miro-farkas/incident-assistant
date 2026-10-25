package dev.mfa.incident.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.mfa.incident.notification.config.NotificationConfig;
import io.quarkus.runtime.configuration.DurationConverter;
import io.smallrye.config.PropertiesConfigSource;
import io.smallrye.config.SmallRyeConfig;
import io.smallrye.config.SmallRyeConfigBuilder;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** The startup checks of the rule configuration (A-0011). */
class NotificationRulesTest {

  private static final Map<String, String> VALID =
      Map.of(
          "notification.ui-base-url", "https://incidents.example.test",
          "notification.rules.approval-needed.state", "AWAITING_APPROVAL",
          "notification.rules.approval-needed.recipients", "approvers",
          "notification.rules.approval-needed.subject", "Approval needed: {service}",
          "notification.recipients.approvers", "a@example.test,b@example.test,a@example.test");

  @Test
  void resolvesRecipientsWithoutDuplicates() {
    NotificationRules rules = new NotificationRules(config(VALID));

    assertThat(rules.forState("AWAITING_APPROVAL"))
        .hasValueSatisfying(
            rule -> {
              assertThat(rule.name()).isEqualTo("approval-needed");
              assertThat(rule.recipients()).isEqualTo(List.of("a@example.test", "b@example.test"));
            });
    assertThat(rules.forState("TRIAGED")).isEmpty();
  }

  @Test
  void rejectsAnUnknownRecipientList() {
    Map<String, String> properties = new HashMap<>(VALID);
    properties.put("notification.rules.approval-needed.recipients", "nobody");

    assertThatThrownBy(() -> new NotificationRules(config(properties)))
        .hasMessageContaining("notification.rules.approval-needed.recipients")
        .hasMessageContaining("nobody");
  }

  @Test
  void rejectsTwoRulesForOneState() {
    Map<String, String> properties = new HashMap<>(VALID);
    properties.put("notification.rules.second.state", "AWAITING_APPROVAL");
    properties.put("notification.rules.second.recipients", "approvers");
    properties.put("notification.rules.second.subject", "Again");

    assertThatThrownBy(() -> new NotificationRules(config(properties)))
        .hasMessageContaining("both trigger on state AWAITING_APPROVAL");
  }

  private static NotificationConfig config(Map<String, String> properties) {
    SmallRyeConfig config =
        new SmallRyeConfigBuilder()
            .withSources(new PropertiesConfigSource(properties, "test", 100))
            // The short duration format of Quarkus ("30s") used by the defaults.
            .withConverter(Duration.class, 100, new DurationConverter())
            .withMapping(NotificationConfig.class)
            .build();
    return config.getConfigMapping(NotificationConfig.class);
  }
}
