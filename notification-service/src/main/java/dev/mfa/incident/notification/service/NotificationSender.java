package dev.mfa.incident.notification.service;

import dev.mfa.incident.notification.channel.NotificationChannel;
import dev.mfa.incident.notification.dto.IncidentDetailsDto;
import dev.mfa.incident.notification.dto.NotificationMessageDto;
import dev.mfa.incident.notification.dto.PendingNotificationDto;
import dev.mfa.incident.notification.model.ChannelType;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import java.util.List;
import java.util.Optional;

/**
 * Sends due notifications (P-0020): claims a batch, then for each one reads the incident details,
 * renders the message and hands it to its channel, and records the outcome. A crash after the
 * channel accepted a message and before it is marked sent resends that one message after the claim
 * timeout.
 */
@ApplicationScoped
public class NotificationSender {

  private final DeliveryRecorder deliveries;
  private final IncidentDetailsService details;
  private final MessageRenderer renderer;
  private final Instance<NotificationChannel> channels;

  public NotificationSender(
      DeliveryRecorder deliveries,
      IncidentDetailsService details,
      MessageRenderer renderer,
      Instance<NotificationChannel> channels) {
    this.deliveries = deliveries;
    this.details = details;
    this.renderer = renderer;
    this.channels = channels;
  }

  /**
   * @return notifications handled in this run, sent or not
   */
  public int sendDue() {
    List<PendingNotificationDto> claimed = deliveries.claimDue();
    claimed.forEach(this::deliver);
    return claimed.size();
  }

  private void deliver(PendingNotificationDto notification) {
    try {
      Optional<IncidentDetailsDto> incident = details.lookup(notification.incidentId());
      NotificationMessageDto message = renderer.render(notification, incident);
      channel(notification.channel()).send(message);
    } catch (RuntimeException e) {
      deliveries.markAttemptFailed(notification.id(), notification.attempts(), e);
      return;
    }
    deliveries.markSent(notification.id());
  }

  private NotificationChannel channel(ChannelType type) {
    return channels.stream()
        .filter(channel -> channel.type() == type)
        .findFirst()
        .orElseThrow(() -> new IllegalStateException("No channel implementation for " + type));
  }
}
