package dev.mfa.incident.notification.repository;

import dev.mfa.incident.notification.model.ProcessedEvent;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.UUID;

/**
 * Append-only. A concurrent insert of the same event fails on {@code uq_processed_event_event_id}.
 */
@ApplicationScoped
public class ProcessedEventRepository implements PanacheRepositoryBase<ProcessedEvent, UUID> {

  public boolean existsByEventId(UUID eventId) {
    return count("eventId", eventId) > 0;
  }
}
