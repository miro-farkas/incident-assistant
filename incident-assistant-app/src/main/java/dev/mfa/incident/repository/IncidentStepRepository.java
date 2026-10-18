package dev.mfa.incident.repository;

import dev.mfa.incident.model.IncidentStep;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;

@ApplicationScoped
public class IncidentStepRepository {

  private final EntityManager entityManager;

  public IncidentStepRepository(EntityManager entityManager) {
    this.entityManager = entityManager;
  }

  public void persist(IncidentStep step) {
    entityManager.persist(step);
  }
}
