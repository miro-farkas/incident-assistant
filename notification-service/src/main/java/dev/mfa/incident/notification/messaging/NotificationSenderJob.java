package dev.mfa.incident.notification.messaging;

import dev.mfa.incident.notification.service.NotificationSender;
import io.quarkus.scheduler.Scheduled;
import io.smallrye.common.annotation.RunOnVirtualThread;
import jakarta.enterprise.context.ApplicationScoped;
import org.jboss.logging.Logger;

/**
 * Runs the sender on every instance at {@code notification.sender.interval}; instances take
 * disjoint batches (P-0020).
 */
@ApplicationScoped
public class NotificationSenderJob {

  private static final Logger LOG = Logger.getLogger(NotificationSenderJob.class);

  private final NotificationSender sender;

  public NotificationSenderJob(NotificationSender sender) {
    this.sender = sender;
  }

  @Scheduled(
      identity = "notification-sender",
      every = "${notification.sender.interval}",
      concurrentExecution = Scheduled.ConcurrentExecution.SKIP,
      skipExecutionIf = Scheduled.ApplicationNotRunning.class)
  @RunOnVirtualThread
  void send() {
    int handled = sender.sendDue();
    if (handled > 0) {
      LOG.debugf("Handled %d notifications", handled);
    }
  }
}
