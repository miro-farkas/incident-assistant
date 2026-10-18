package dev.mfa.incident.repository;

import dev.mfa.incident.model.OutboxEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;

@ApplicationScoped
public class OutboxEventRepository {

  private final EntityManager entityManager;

  public OutboxEventRepository(EntityManager entityManager) {
    this.entityManager = entityManager;
  }

  public void persist(OutboxEvent event) {
    entityManager.persist(event);
  }
}
