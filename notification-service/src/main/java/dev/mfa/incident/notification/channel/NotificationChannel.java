package dev.mfa.incident.notification.channel;

import dev.mfa.incident.notification.dto.NotificationMessageDto;
import dev.mfa.incident.notification.model.ChannelType;

/** Delivers rendered messages over one channel (P-0020); one implementation per channel type. */
public interface NotificationChannel {

  ChannelType type();

  /**
   * Hands the message to the channel; returns once the channel accepted it.
   *
   * @throws RuntimeException if the channel refused it or did not answer in time
   */
  void send(NotificationMessageDto message);
}
