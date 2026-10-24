package dev.mfa.incident.notification.service;

import dev.mfa.incident.notification.config.NotificationConfig;
import dev.mfa.incident.notification.dto.PendingNotificationDto;
import dev.mfa.incident.notification.mapper.NotificationMapper;
import dev.mfa.incident.notification.model.Notification;
import dev.mfa.incident.notification.model.NotificationStatus;
import dev.mfa.incident.notification.repository.NotificationRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.jboss.logging.Logger;

/**
 * The delivery state of notifications, each change in a short transaction of its own, so no
 * database transaction stays open while a channel is called (P-0020).
 */
@ApplicationScoped
public class DeliveryRecorder {

  private static final Logger LOG = Logger.getLogger(DeliveryRecorder.class);

  /** Longest error text stored (column {@code last_error}). */
  static final int MAX_ERROR_LENGTH = 1000;

  private final NotificationRepository notifications;
  private final NotificationMapper mapper;
  private final NotificationConfig.Sender config;

  public DeliveryRecorder(
      NotificationRepository notifications, NotificationMapper mapper, NotificationConfig config) {
    this.notifications = notifications;
    this.mapper = mapper;
    this.config = config.sender();
  }

  /**
   * Claims up to {@code batch-size} due notifications ({@code SKIP LOCKED}, so concurrent senders
   * take disjoint ones), counts the attempt and reserves them for {@code claim-timeout}.
   */
  @Transactional
  public List<PendingNotificationDto> claimDue() {
    Instant now = Instant.now();
    List<Notification> due = notifications.lockDue(now, config.batchSize());
    for (Notification notification : due) {
      notification.setAttempts(notification.getAttempts() + 1);
      notification.setNextAttemptAt(now.plus(config.claimTimeout()));
    }
    return due.stream().map(mapper::toPendingDto).toList();
  }

  @Transactional
  public void markSent(UUID notificationId) {
    Notification notification = notifications.findById(notificationId);
    notification.setStatus(NotificationStatus.SENT);
    notification.setSentAt(Instant.now());
    notification.setLastError(null);
  }

  /**
   * Schedules the next attempt after {@code backoff-base * 2^(attempts - 1)}, or marks the
   * notification failed after {@code max-attempts}.
   *
   * @param attempts attempts made, including the failed one
   */
  @Transactional
  public void markAttemptFailed(UUID notificationId, int attempts, Throwable failure) {
    Notification notification = notifications.findById(notificationId);
    notification.setLastError(errorText(failure));
    if (attempts >= config.maxAttempts()) {
      notification.setStatus(NotificationStatus.FAILED);
      LOG.warnf(
          "Notification %s failed after %d attempts (%s)",
          notificationId, attempts, failure.getClass().getSimpleName());
      return;
    }
    Duration backoff = config.backoffBase().multipliedBy(1L << (attempts - 1));
    notification.setNextAttemptAt(Instant.now().plus(backoff));
    LOG.infof(
        "Notification %s attempt %d failed (%s); next attempt in %s",
        notificationId, attempts, failure.getClass().getSimpleName(), backoff);
  }

  private static String errorText(Throwable failure) {
    String text =
        failure.getClass().getName()
            + (failure.getMessage() == null ? "" : ": " + failure.getMessage());
    return text.length() > MAX_ERROR_LENGTH ? text.substring(0, MAX_ERROR_LENGTH) : text;
  }
}
