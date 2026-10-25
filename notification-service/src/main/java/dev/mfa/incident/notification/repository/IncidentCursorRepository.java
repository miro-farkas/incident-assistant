package dev.mfa.incident.notification.repository;

import dev.mfa.incident.notification.model.IncidentCursor;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class IncidentCursorRepository implements PanacheRepositoryBase<IncidentCursor, UUID> {

  /**
   * The cursor of the incident, locked with {@code FOR UPDATE} until the transaction ends, so two
   * events of one incident, a redelivery among them, are compared one after the other. A first
   * event of an incident finds none; two of those at once collide on {@code
   * uq_incident_cursor_incident}.
   */
  public Optional<IncidentCursor> findForUpdate(UUID incidentId) {
    return find("incidentId", incidentId)
        .withLock(LockModeType.PESSIMISTIC_WRITE)
        .firstResultOptional();
  }
}
