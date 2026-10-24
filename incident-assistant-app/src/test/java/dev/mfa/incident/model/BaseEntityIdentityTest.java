package dev.mfa.incident.model;

import static dev.mfa.incident.testsupport.WorkflowFixtures.newIncident;
import static org.assertj.core.api.Assertions.assertThat;

import dev.mfa.incident.mapper.IncidentMapper;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import org.hibernate.Hibernate;
import org.junit.jupiter.api.Test;

/** Equality of entities is row identity (P-0014), checked against the database. */
@QuarkusTest
class BaseEntityIdentityTest {

  @Inject EntityManager entityManager;
  @Inject IncidentMapper mapper;

  @Test
  void newEntityEqualsOnlyItselfAndKeepsItsHashWhenPersisted() {
    Incident incident = unsaved();
    Set<Incident> set = new HashSet<>(Set.of(incident));

    assertThat(incident).isEqualTo(incident).isNotEqualTo(unsaved());
    QuarkusTransaction.requiringNew().run(() -> entityManager.persist(incident));

    assertThat(incident.getId()).isNotNull();
    assertThat(set).contains(incident);
  }

  @Test
  void sameRowLoadedTwiceIsEqual() {
    UUID id = persisted();

    Incident first = QuarkusTransaction.requiringNew().call(() -> load(id));
    Incident second = QuarkusTransaction.requiringNew().call(() -> load(id));

    assertThat(first).isNotSameAs(second).isEqualTo(second).hasSameHashCodeAs(second);
    assertThat(first).isNotEqualTo(QuarkusTransaction.requiringNew().call(() -> load(persisted())));
  }

  /**
   * With bytecode enhancement (Quarkus) Hibernate returns an uninitialized instance of the entity
   * class itself instead of a proxy subclass; without it, a {@code HibernateProxy}. Either way it
   * equals the loaded entity.
   */
  @Test
  void uninitializedReferenceEqualsTheLoadedEntity() {
    UUID id = persisted();
    Incident loaded = QuarkusTransaction.requiringNew().call(() -> load(id));

    QuarkusTransaction.requiringNew()
        .run(
            () -> {
              Incident reference = entityManager.getReference(Incident.class, id);
              assertThat(Hibernate.isInitialized(reference)).isFalse();
              assertThat(reference).isEqualTo(loaded).hasSameHashCodeAs(loaded);
              assertThat(loaded).isEqualTo(reference);
            });
  }

  private Incident unsaved() {
    Incident incident = mapper.toEntity(newIncident());
    incident.setState(IncidentState.RECEIVED);
    return incident;
  }

  private UUID persisted() {
    Incident incident = unsaved();
    QuarkusTransaction.requiringNew().run(() -> entityManager.persist(incident));
    return incident.getId();
  }

  private Incident load(UUID id) {
    return entityManager.find(Incident.class, id);
  }
}
