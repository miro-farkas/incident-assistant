package dev.mfa.incident.notification.service;

import dev.mfa.incident.notification.config.NotificationConfig;
import dev.mfa.incident.notification.dto.NotificationRuleDto;
import io.quarkus.runtime.Startup;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The rules of {@code notification.rules}, checked at startup: every rule names an existing
 * recipient list, and no two rules trigger on the same state. A violation stops the application.
 */
@Startup
@ApplicationScoped
public class NotificationRules {

  private final Map<String, NotificationRuleDto> byState = new HashMap<>();

  public NotificationRules(NotificationConfig config) {
    config
        .rules()
        .forEach(
            (name, rule) -> {
              List<String> recipients = config.recipients().get(rule.recipients());
              if (recipients == null) {
                throw new IllegalStateException(
                    "notification.rules."
                        + name
                        + ".recipients names an unknown list: "
                        + rule.recipients());
              }
              NotificationRuleDto resolved =
                  new NotificationRuleDto(
                      name, rule.state(), recipients.stream().distinct().toList());
              NotificationRuleDto other = byState.putIfAbsent(rule.state(), resolved);
              if (other != null) {
                throw new IllegalStateException(
                    "notification.rules."
                        + name
                        + " and notification.rules."
                        + other.name()
                        + " both trigger on state "
                        + rule.state());
              }
            });
  }

  public Optional<NotificationRuleDto> forState(String state) {
    return Optional.ofNullable(byState.get(state));
  }
}
