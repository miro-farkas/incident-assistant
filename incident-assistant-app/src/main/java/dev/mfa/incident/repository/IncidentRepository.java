package dev.mfa.incident.repository;

import dev.mfa.incident.exception.IncidentVersionConflictException;
import dev.mfa.incident.model.Incident;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.persistence.OptimisticLockException;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class IncidentRepository {

  private final EntityManager entityManager;

  public IncidentRepository(EntityManager entityManager) {
    this.entityManager = entityManager;
  }

  public Optional<Incident> findById(UUID id) {
    return Optional.ofNullable(entityManager.find(Incident.class, id));
  }

  public void persist(Incident incident) {
    entityManager.persist(incident);
  }

  /**
   * Writes pending changes, so that constraint violations surface in the caller and generated
   * values (id, timestamps, version) are available afterwards.
   */
  public void flush() {
    entityManager.flush();
  }

  /**
   * Like {@link #flush()}, but reports a concurrent update of the incident as a domain exception.
   *
   * @throws IncidentVersionConflictException if another transaction updated the incident first
   */
  public void flush(Incident incident, long expectedVersion) {
    try {
      entityManager.flush();
    } catch (OptimisticLockException e) {
      throw new IncidentVersionConflictException(incident.getId(), expectedVersion);
    }
  }
}
