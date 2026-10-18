package dev.mfa.incident.outbox.persistence;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;

@ApplicationScoped
public class OutboxEventRepository {

  private final EntityManager entityManager;

  public OutboxEventRepository(EntityManager entityManager) {
    this.entityManager = entityManager;
  }

  public void persist(OutboxEventEntity event) {
    entityManager.persist(event);
  }
}
