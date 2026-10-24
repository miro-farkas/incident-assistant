package dev.mfa.incident.notification.repository;

import dev.mfa.incident.notification.model.Notification;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@ApplicationScoped
public class NotificationRepository implements PanacheRepositoryBase<Notification, UUID> {

  /**
   * Pending notifications due at {@code now}, oldest first, locked until the transaction ends. Rows
   * another transaction holds are skipped ({@code SKIP LOCKED}), so several senders take disjoint
   * batches without waiting for each other (index {@code ix_notification_due}).
   */
  @SuppressWarnings("unchecked")
  public List<Notification> lockDue(Instant now, int limit) {
    return getEntityManager()
        .createNativeQuery(
            "select * from notification where status = 'PENDING' and next_attempt_at <= :now"
                + " order by next_attempt_at limit :limit for update skip locked",
            Notification.class)
        .setParameter("now", now)
        .setParameter("limit", limit)
        .getResultList();
  }
}
