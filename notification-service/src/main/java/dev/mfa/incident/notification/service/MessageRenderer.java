package dev.mfa.incident.notification.service;

import dev.mfa.incident.notification.config.NotificationConfig;
import dev.mfa.incident.notification.dto.IncidentDetailsDto;
import dev.mfa.incident.notification.dto.NotificationMessageDto;
import dev.mfa.incident.notification.dto.PendingNotificationDto;
import io.quarkus.qute.Engine;
import io.quarkus.qute.Location;
import io.quarkus.qute.Template;
import io.quarkus.qute.TemplateInstance;
import io.quarkus.runtime.Startup;
import jakarta.enterprise.context.ApplicationScoped;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Renders messages from the subject of their rule and the templates {@code notification/message.*}
 * (A-0011). Alert values are untrusted: control characters are replaced and long values cut, so
 * they cannot add lines or headers, and the HTML template escapes them. Only the fields listed in
 * A-0011 are shown; the link points to the configured UI. Subjects are parsed at startup, so an
 * invalid one stops the application.
 */
@Startup
@ApplicationScoped
public class MessageRenderer {

  /** Longest alert value shown; longer ones are cut. */
  static final int MAX_VALUE_LENGTH = 100;

  static final String UNKNOWN = "unknown";

  private final Map<String, Template> subjects = new HashMap<>();
  private final Template text;
  private final Template html;
  private final String incidentsUrl;

  public MessageRenderer(
      NotificationConfig config,
      Engine engine,
      @Location("notification/message.txt") Template text,
      @Location("notification/message.html") Template html) {
    config.rules().forEach((name, rule) -> subjects.put(name, engine.parse(rule.subject())));
    this.text = text;
    this.html = html;
    this.incidentsUrl = config.uiBaseUrl().replaceAll("/+$", "") + "/incidents/";
  }

  /**
   * @param details empty when the incident API could not be read: the minimal message (incident,
   *     state, reason, time, link)
   */
  public NotificationMessageDto render(
      PendingNotificationDto notification, Optional<IncidentDetailsDto> details) {
    Map<String, Object> data = new HashMap<>();
    data.put("service", value(details.map(IncidentDetailsDto::service)));
    data.put("severity", value(details.map(IncidentDetailsDto::severity)));
    data.put("alertName", value(details.map(IncidentDetailsDto::alertName)));
    data.put("state", notification.state());
    data.put("reasonCode", notification.reasonCode() == null ? "" : notification.reasonCode());
    data.put("detailsAvailable", details.isPresent());
    data.put("incidentId", notification.incidentId().toString());
    data.put("occurredAt", notification.occurredAt().truncatedTo(ChronoUnit.SECONDS).toString());
    data.put("link", incidentsUrl + notification.incidentId());

    Template subject = subjects.get(notification.rule());
    if (subject == null) {
      throw new IllegalStateException("No rule " + notification.rule() + " is configured");
    }
    String subjectText = singleLine(withData(subject, data).render());
    data.put("subject", subjectText);
    return new NotificationMessageDto(
        notification.recipient(),
        subjectText,
        withData(text, data).render(),
        withData(html, data).render());
  }

  private static TemplateInstance withData(Template template, Map<String, Object> data) {
    TemplateInstance instance = template.instance();
    data.forEach(instance::data);
    return instance;
  }

  private static String value(Optional<String> untrusted) {
    String line = untrusted.map(MessageRenderer::singleLine).orElse("");
    if (line.isEmpty()) {
      return UNKNOWN;
    }
    return line.length() > MAX_VALUE_LENGTH ? line.substring(0, MAX_VALUE_LENGTH) + "…" : line;
  }

  /** Control characters (line breaks among them) become spaces; runs of spaces become one. */
  private static String singleLine(String text) {
    return text.replaceAll("\\p{Cntrl}", " ").replaceAll(" {2,}", " ").strip();
  }
}
