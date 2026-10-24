package dev.mfa.incident.notification.service;

import dev.mfa.incident.notification.dto.EventRecordingResult;
import dev.mfa.incident.notification.dto.IncidentStateEventDto;
import dev.mfa.incident.notification.dto.NotificationRuleDto;
import dev.mfa.incident.notification.mapper.NotificationMapper;
import dev.mfa.incident.notification.model.ChannelType;
import dev.mfa.incident.notification.model.IncidentCursor;
import dev.mfa.incident.notification.repository.IncidentCursorRepository;
import dev.mfa.incident.notification.repository.NotificationRepository;
import dev.mfa.incident.notification.repository.ProcessedEventRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import java.time.Instant;
import java.util.Optional;

/**
 * Records one incident event in one transaction (P-0020): the event as processed, the incident's
 * newest version, and one pending notification per recipient of the matching rule. Nothing is sent
 * here.
 */
@ApplicationScoped
public class EventRecorder {

  private final ProcessedEventRepository processedEvents;
  private final IncidentCursorRepository cursors;
  private final NotificationRepository notifications;
  private final NotificationRules rules;
  private final NotificationMapper mapper;

  public EventRecorder(
      ProcessedEventRepository processedEvents,
      IncidentCursorRepository cursors,
      NotificationRepository notifications,
      NotificationRules rules,
      NotificationMapper mapper) {
    this.processedEvents = processedEvents;
    this.cursors = cursors;
    this.notifications = notifications;
    this.rules = rules;
    this.mapper = mapper;
  }

  /**
   * A concurrent recording of the same event, or of the first event of the same incident, fails on
   * a unique constraint and rolls back; the caller retries and then sees the committed one.
   */
  @Transactional
  public EventRecordingResult record(IncidentStateEventDto event) {
    if (processedEvents.existsByEventId(event.eventId())) {
      return new EventRecordingResult.Duplicate();
    }
    processedEvents.persist(mapper.toProcessedEvent(event));

    Optional<IncidentCursor> cursor = cursors.findForUpdate(event.incidentId());
    if (cursor.isPresent() && cursor.get().getLastVersion() >= event.incidentVersion()) {
      return new EventRecordingResult.Outdated(cursor.get().getLastVersion());
    }
    cursor.ifPresentOrElse(
        c -> c.setLastVersion(event.incidentVersion()),
        () -> cursors.persist(mapper.toCursor(event)));

    Optional<NotificationRuleDto> rule = rules.forState(event.state());
    if (rule.isEmpty()) {
      return new EventRecordingResult.Recorded(0);
    }
    Instant now = Instant.now();
    for (String recipient : rule.get().recipients()) {
      notifications.persist(
          mapper.toPendingNotification(
              event, rule.get().name(), ChannelType.EMAIL, recipient, now));
    }
    return new EventRecordingResult.Recorded(rule.get().recipients().size());
  }
}
