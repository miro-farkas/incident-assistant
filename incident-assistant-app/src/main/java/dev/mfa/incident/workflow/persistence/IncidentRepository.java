package dev.mfa.incident.workflow.persistence;

import dev.mfa.incident.workflow.IncidentVersionConflictException;
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

  public Optional<IncidentEntity> findById(UUID id) {
    return Optional.ofNullable(entityManager.find(IncidentEntity.class, id));
  }

  public void persist(IncidentEntity incident) {
    entityManager.persist(incident);
  }

  public void persist(IncidentStepEntity step) {
    entityManager.persist(step);
  }

  /** Writes pending changes, so that constraint violations surface inside the caller. */
  public void flush() {
    entityManager.flush();
  }

  /**
   * Writes pending changes so that the new version is known and a concurrent update surfaces here
   * instead of at commit.
   *
   * @throws IncidentVersionConflictException if another transaction updated the incident first
   */
  public void flush(IncidentEntity incident, long expectedVersion) {
    try {
      entityManager.flush();
    } catch (OptimisticLockException e) {
      throw new IncidentVersionConflictException(incident.id(), expectedVersion);
    }
  }
}
