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
    IncidentCursor cursor = unsaved();
    Set<IncidentCursor> set = new HashSet<>(Set.of(cursor));

    assertThat(cursor).isEqualTo(cursor).isNotEqualTo(unsaved());
    QuarkusTransaction.requiringNew().run(() -> entityManager.persist(cursor));

    assertThat(cursor.getId()).isNotNull();
    assertThat(set).contains(cursor);
  }

  @Test
  void sameRowLoadedTwiceIsEqual() {
    UUID id = persisted();

    IncidentCursor first = QuarkusTransaction.requiringNew().call(() -> load(id));
    IncidentCursor second = QuarkusTransaction.requiringNew().call(() -> load(id));

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
    IncidentCursor loaded = QuarkusTransaction.requiringNew().call(() -> load(id));

    QuarkusTransaction.requiringNew()
        .run(
            () -> {
              IncidentCursor reference = entityManager.getReference(IncidentCursor.class, id);
              assertThat(Hibernate.isInitialized(reference)).isFalse();
              assertThat(reference).isEqualTo(loaded).hasSameHashCodeAs(loaded);
              assertThat(loaded).isEqualTo(reference);
            });
  }

  private static IncidentCursor unsaved() {
    IncidentCursor cursor = new IncidentCursor();
    cursor.setIncidentId(UUID.randomUUID());
    cursor.setLastVersion(1);
    return cursor;
  }

  private UUID persisted() {
    IncidentCursor cursor = unsaved();
    QuarkusTransaction.requiringNew().run(() -> entityManager.persist(cursor));
    return cursor.getId();
  }

  private IncidentCursor load(UUID id) {
    return entityManager.find(IncidentCursor.class, id);
  }
}
