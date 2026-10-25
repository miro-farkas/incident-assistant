package dev.mfa.incident.notification.mapper;

import dev.mfa.incident.notification.dto.IncidentStateEventDto;
import dev.mfa.incident.notification.dto.PendingNotificationDto;
import dev.mfa.incident.notification.model.ChannelType;
import dev.mfa.incident.notification.model.IncidentCursor;
import dev.mfa.incident.notification.model.Notification;
import dev.mfa.incident.notification.model.NotificationStatus;
import jakarta.enterprise.context.ApplicationScoped;
import java.time.Instant;

@ApplicationScoped
public class NotificationMapper {

  public IncidentCursor toCursor(IncidentStateEventDto event) {
    IncidentCursor cursor = new IncidentCursor();
    cursor.setIncidentId(event.incidentId());
    cursor.setLastVersion(event.incidentVersion());
    return cursor;
  }

  /** A new notification, due at once, without attempts. */
  public Notification toPendingNotification(
      IncidentStateEventDto event,
      String rule,
      ChannelType channel,
      String recipient,
      Instant now) {
    Notification notification = new Notification();
    notification.setEventId(event.eventId());
    notification.setIncidentId(event.incidentId());
    notification.setRule(rule);
    notification.setState(event.state());
    notification.setReasonCode(event.reasonCode());
    notification.setOccurredAt(event.occurredAt());
    notification.setChannel(channel);
    notification.setRecipient(recipient);
    notification.setStatus(NotificationStatus.PENDING);
    notification.setAttempts(0);
    notification.setNextAttemptAt(now);
    return notification;
  }

  public PendingNotificationDto toPendingDto(Notification notification) {
    return new PendingNotificationDto(
        notification.getId(),
        notification.getIncidentId(),
        notification.getRule(),
        notification.getState(),
        notification.getReasonCode(),
        notification.getOccurredAt(),
        notification.getChannel(),
        notification.getRecipient(),
        notification.getAttempts());
  }
}
