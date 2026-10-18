package dev.mfa.incident.repository;

import dev.mfa.incident.exception.IncidentVersionConflictException;
import dev.mfa.incident.model.Incident;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.OptimisticLockException;
import java.util.UUID;

@ApplicationScoped
public class IncidentRepository implements PanacheRepositoryBase<Incident, UUID> {

  /**
   * Writes pending changes and reports a concurrent update of the incident as a domain exception,
   * so the new version is known afterwards and the conflict surfaces here instead of at commit.
   *
   * @throws IncidentVersionConflictException if another transaction updated the incident first
   */
  public void flush(Incident incident, long expectedVersion) {
    try {
      flush();
    } catch (OptimisticLockException e) {
      throw new IncidentVersionConflictException(incident.getId(), expectedVersion);
    }
  }
}
