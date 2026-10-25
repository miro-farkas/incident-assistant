package dev.mfa.incident.notification.config;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.hibernate.validator.constraints.URL;
import org.hibernate.validator.constraints.time.DurationMin;

/**
 * Who is notified about which incident state, and how the sender works (A-0011). References between
 * rules and recipient lists are checked at startup by {@code NotificationRules}.
 */
@ConfigMapping(prefix = "notification")
public interface NotificationConfig {

  /** Rule names: lower-case letters, digits and '-', as stored with each notification. */
  String RULE_NAME_PATTERN = "[a-z0-9][a-z0-9-]{0,63}";

  /** Address of the incident UI; messages link to {@code <ui-base-url>/incidents/<id>}. */
  @NotBlank
  @URL(regexp = "^https?:.*")
  String uiBaseUrl();

  /** Rules by name. */
  @NotEmpty
  Map<@Pattern(regexp = RULE_NAME_PATTERN) String, Rule> rules();

  /** Recipient lists by name: e-mail addresses. */
  @NotEmpty
  Map<String, @NotEmpty List<@NotBlank @Email @Size(max = 320) String>> recipients();

  Sender sender();

  /** Notifies a recipient list when an incident enters a state. */
  interface Rule {

    /** The incident state (A-0007 {@code state}) that triggers the rule; one rule per state. */
    @NotBlank
    @Pattern(regexp = "[A-Z_]{1,32}")
    String state();

    /** Name of the recipient list in {@code notification.recipients}. */
    @NotBlank
    String recipients();

    /**
     * Subject as a Qute template; placeholders {@code service}, {@code severity}, {@code
     * alertName}, {@code state} and {@code reasonCode}.
     */
    @NotBlank
    String subject();
  }

  /** The scheduled sender (P-0020). */
  interface Sender {

    /** Pause between sender runs. */
    @WithDefault("5s")
    @DurationMin(millis = 1)
    Duration interval();

    /** Notifications claimed per run. */
    @WithDefault("20")
    @Min(1)
    @Max(500)
    int batchSize();

    /** Attempts per notification before it is marked failed. */
    @WithDefault("5")
    @Min(1)
    @Max(20)
    int maxAttempts();

    /** Wait before the second attempt; doubles with every further attempt. */
    @WithDefault("30s")
    @DurationMin(millis = 1)
    Duration backoffBase();

    /**
     * How long a claimed notification stays reserved for the sender that claimed it. A sender that
     * crashes leaves it to be claimed again after this time, so it must exceed the time one batch
     * needs.
     */
    @WithDefault("5m")
    @DurationMin(seconds = 1)
    Duration claimTimeout();
  }
}
