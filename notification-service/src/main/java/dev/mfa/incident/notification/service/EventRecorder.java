package dev.mfa.incident.notification.service;

import dev.mfa.incident.notification.dto.EventRecordingResult;
import dev.mfa.incident.notification.dto.IncidentStateEventDto;
import dev.mfa.incident.notification.dto.NotificationRuleDto;
import dev.mfa.incident.notification.mapper.NotificationMapper;
import dev.mfa.incident.notification.model.ChannelType;
import dev.mfa.incident.notification.model.IncidentCursor;
import dev.mfa.incident.notification.repository.IncidentCursorRepository;
import dev.mfa.incident.notification.repository.NotificationRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import java.time.Instant;
import java.util.Optional;

/**
 * Records one incident event in one transaction (P-0020): the incident's newest version, and one
 * pending notification per recipient of the matching rule. An event whose version is not newer than
 * the one seen, a redelivery among them, changes nothing. Nothing is sent here.
 */
@ApplicationScoped
public class EventRecorder {

  private final IncidentCursorRepository cursors;
  private final NotificationRepository notifications;
  private final NotificationRules rules;
  private final NotificationMapper mapper;

  public EventRecorder(
      IncidentCursorRepository cursors,
      NotificationRepository notifications,
      NotificationRules rules,
      NotificationMapper mapper) {
    this.cursors = cursors;
    this.notifications = notifications;
    this.rules = rules;
    this.mapper = mapper;
  }

  /**
   * Events of one incident are compared under the lock of its cursor. Two first events of one
   * incident at once collide on the cursor's unique key and one rolls back; the caller retries and
   * then compares against the committed one.
   */
  @Transactional
  public EventRecordingResult record(IncidentStateEventDto event) {
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
