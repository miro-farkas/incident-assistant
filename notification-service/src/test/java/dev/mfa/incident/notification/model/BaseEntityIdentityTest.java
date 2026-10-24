package dev.mfa.incident.notification.model;

import static org.assertj.core.api.Assertions.assertThat;

import dev.mfa.incident.notification.testsupport.ManualSenderProfile;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import org.hibernate.Hibernate;
import org.junit.jupiter.api.Test;

/** Equality of entities is row identity (P-0014), checked against the database. */
@QuarkusTest
@TestProfile(ManualSenderProfile.class)
class BaseEntityIdentityTest {

  @Inject EntityManager entityManager;

  @Test
  void newEntityEqualsOnlyItselfAndKeepsItsHashWhenPersisted() {
    ProcessedEvent event = unsaved();
    Set<ProcessedEvent> set = new HashSet<>(Set.of(event));

    assertThat(event).isEqualTo(event).isNotEqualTo(unsaved());
    QuarkusTransaction.requiringNew().run(() -> entityManager.persist(event));

    assertThat(event.getId()).isNotNull();
    assertThat(set).contains(event);
  }

  @Test
  void sameRowLoadedTwiceIsEqual() {
    UUID id = persisted();

    ProcessedEvent first = QuarkusTransaction.requiringNew().call(() -> load(id));
    ProcessedEvent second = QuarkusTransaction.requiringNew().call(() -> load(id));

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
    ProcessedEvent loaded = QuarkusTransaction.requiringNew().call(() -> load(id));

    QuarkusTransaction.requiringNew()
        .run(
            () -> {
              ProcessedEvent reference = entityManager.getReference(ProcessedEvent.class, id);
              assertThat(Hibernate.isInitialized(reference)).isFalse();
              assertThat(reference).isEqualTo(loaded).hasSameHashCodeAs(loaded);
              assertThat(loaded).isEqualTo(reference);
            });
  }

  private static ProcessedEvent unsaved() {
    ProcessedEvent event = new ProcessedEvent();
    event.setEventId(UUID.randomUUID());
    event.setIncidentId(UUID.randomUUID());
    event.setIncidentVersion(1);
    return event;
  }

  private UUID persisted() {
    ProcessedEvent event = unsaved();
    QuarkusTransaction.requiringNew().run(() -> entityManager.persist(event));
    return event.getId();
  }

  private ProcessedEvent load(UUID id) {
    return entityManager.find(ProcessedEvent.class, id);
  }
}
