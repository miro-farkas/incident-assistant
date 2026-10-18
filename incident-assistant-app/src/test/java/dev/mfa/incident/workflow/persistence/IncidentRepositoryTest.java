package dev.mfa.incident.workflow.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.mfa.incident.workflow.Incident;
import dev.mfa.incident.workflow.IncidentService;
import dev.mfa.incident.workflow.IncidentState;
import dev.mfa.incident.workflow.IncidentVersionConflictException;
import dev.mfa.incident.workflow.WorkflowFixtures;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import org.junit.jupiter.api.Test;

@QuarkusTest
class IncidentRepositoryTest {

  @Inject IncidentService service;
  @Inject IncidentRepository repository;
  @Inject EntityManager entityManager;

  /**
   * Another transaction commits a change between this transaction reading and writing the incident.
   * The {@code @Version} check must turn that into a domain exception at flush time.
   */
  @Test
  void reportsConcurrentUpdateAsVersionConflict() {
    Incident incident = service.create(WorkflowFixtures.newIncident());

    assertThatThrownBy(
            () ->
                QuarkusTransaction.requiringNew()
                    .run(
                        () -> {
                          IncidentEntity entity = repository.findById(incident.id()).orElseThrow();
                          QuarkusTransaction.requiringNew()
                              .run(
                                  () ->
                                      entityManager
                                          .createNativeQuery(
                                              "update incident set version = version + 1"
                                                  + " where id = :id")
                                          .setParameter("id", incident.id())
                                          .executeUpdate());
                          entity.transitionTo(IncidentState.TRIAGED, Instant.now());
                          repository.flush(entity, incident.version());
                        }))
        .isInstanceOfSatisfying(
            IncidentVersionConflictException.class,
            e -> assertThat(e.expectedVersion()).isEqualTo(incident.version()));

    assertThat(service.get(incident.id()).state()).isEqualTo(IncidentState.RECEIVED);
  }
}
