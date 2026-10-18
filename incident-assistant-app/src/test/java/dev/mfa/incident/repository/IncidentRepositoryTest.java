package dev.mfa.incident.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.mfa.incident.dto.IncidentDto;
import dev.mfa.incident.exception.IncidentVersionConflictException;
import dev.mfa.incident.model.Incident;
import dev.mfa.incident.model.IncidentState;
import dev.mfa.incident.service.IncidentService;
import dev.mfa.incident.testsupport.WorkflowFixtures;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
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
    IncidentDto incident = service.create(WorkflowFixtures.newIncident());

    assertThatThrownBy(
            () ->
                QuarkusTransaction.requiringNew()
                    .run(
                        () -> {
                          Incident entity = repository.findById(incident.id()).orElseThrow();
                          QuarkusTransaction.requiringNew()
                              .run(
                                  () ->
                                      entityManager
                                          .createNativeQuery(
                                              "update incident set version = version + 1"
                                                  + " where id = :id")
                                          .setParameter("id", incident.id())
                                          .executeUpdate());
                          entity.setState(IncidentState.TRIAGED);
                          repository.flush(entity, incident.version());
                        }))
        .isInstanceOfSatisfying(
            IncidentVersionConflictException.class,
            e -> assertThat(e.expectedVersion()).isEqualTo(incident.version()));

    assertThat(service.get(incident.id()).state()).isEqualTo(IncidentState.RECEIVED);
  }
}
