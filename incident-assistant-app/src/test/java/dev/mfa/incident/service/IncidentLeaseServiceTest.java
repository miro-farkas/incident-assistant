package dev.mfa.incident.service;

import static dev.mfa.incident.testsupport.WorkflowFixtures.newIncident;
import static org.assertj.core.api.Assertions.assertThat;

import dev.mfa.incident.dto.ClaimedIncidentDto;
import dev.mfa.incident.dto.IncidentDto;
import dev.mfa.incident.model.Incident;
import dev.mfa.incident.model.IncidentState;
import dev.mfa.incident.repository.IncidentRepository;
import dev.mfa.incident.testsupport.WorkflowDriver;
import dev.mfa.incident.testsupport.WorkflowTables;
import dev.mfa.incident.testsupport.WorkflowTables.LeaseRow;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

/**
 * Leases of the step executor (P-0003, A-0014): SKIP LOCKED claims, the version stays, expired
 * leases are claimable again. The executor itself is off in this profile.
 */
@QuarkusTest
class IncidentLeaseServiceTest {

  private static final Set<IncidentState> RECEIVED = Set.of(IncidentState.RECEIVED);

  @Inject IncidentLeaseService leases;
  @Inject IncidentService incidents;
  @Inject IncidentRepository repository;
  @Inject WorkflowDriver workflow;
  @Inject WorkflowTables tables;
  @Inject EntityManager entityManager;

  @Test
  void claimSetsTheLeaseWithoutChangingTheVersion() {
    IncidentDto created = incidents.create(newIncident());

    List<ClaimedIncidentDto> claimed = claimUntilFound(created.id());

    assertThat(claimed).anySatisfy(c -> assertThat(c.version()).isEqualTo(created.version()));
    LeaseRow lease = tables.lease(created.id());
    assertThat(lease.owner()).isEqualTo("test-instance");
    assertThat(lease.expiresAt()).isAfter(Instant.now());
    assertThat(lease.version()).isEqualTo(created.version());
    assertThat(leases.claim(RECEIVED, 1000)).noneMatch(c -> c.id().equals(created.id()));
  }

  @Test
  void expiredLeaseIsClaimableAgain() {
    IncidentDto created = incidents.create(newIncident());
    claimUntilFound(created.id());
    QuarkusTransaction.requiringNew()
        .run(
            () ->
                entityManager
                    .createNativeQuery(
                        "update incident set lease_expires_at = now() - interval '1 second'"
                            + " where id = :id")
                    .setParameter("id", created.id())
                    .executeUpdate());

    assertThat(claimUntilFound(created.id())).isNotEmpty();
  }

  @Test
  void onlyStatesWithAStepAreClaimed() {
    IncidentDto triaged = workflow.triaged();

    assertThat(leases.claim(RECEIVED, 1000)).noneMatch(c -> c.id().equals(triaged.id()));
  }

  @Test
  void concurrentClaimsGetDisjointIncidents() throws Exception {
    for (int i = 0; i < 3; i++) {
      incidents.create(newIncident());
    }
    CountDownLatch locked = new CountDownLatch(1);
    CountDownLatch release = new CountDownLatch(1);
    CompletableFuture<List<UUID>> other =
        CompletableFuture.supplyAsync(
            () ->
                QuarkusTransaction.requiringNew()
                    .call(
                        () -> {
                          List<UUID> ids =
                              repository.lockClaimable(RECEIVED, Instant.now(), 1000).stream()
                                  .map(Incident::getId)
                                  .toList();
                          locked.countDown();
                          release.await(10, TimeUnit.SECONDS);
                          return ids;
                        }));
    assertThat(locked.await(10, TimeUnit.SECONDS)).isTrue();

    List<UUID> claimed;
    try {
      claimed =
          CompletableFuture.supplyAsync(() -> leases.claim(RECEIVED, 1000))
              .get(5, TimeUnit.SECONDS)
              .stream()
              .map(ClaimedIncidentDto::id)
              .toList();
    } finally {
      release.countDown();
    }

    assertThat(claimed).doesNotContainAnyElementsOf(other.get(10, TimeUnit.SECONDS));
  }

  @Test
  void recordingTheStepReleasesTheLease() {
    IncidentDto created = incidents.create(newIncident());
    claimUntilFound(created.id());

    workflow.advanceToTriaged(created);

    assertThat(tables.lease(created.id()).owner()).isNull();
    assertThat(tables.lease(created.id()).expiresAt()).isNull();
  }

  /** Other tests leave unclaimed incidents behind; claims take the oldest first. */
  private List<ClaimedIncidentDto> claimUntilFound(UUID id) {
    List<ClaimedIncidentDto> claimed = leases.claim(RECEIVED, 100_000);
    assertThat(claimed).extracting(ClaimedIncidentDto::id).contains(id);
    return claimed;
  }
}
