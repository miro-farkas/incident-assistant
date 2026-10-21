package dev.mfa.incident.repository;

import dev.mfa.incident.model.IncidentStep;
import dev.mfa.incident.model.StepType;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import io.quarkus.panache.common.Sort;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Append-only: services only insert step rows (P-0014). */
@ApplicationScoped
public class IncidentStepRepository implements PanacheRepositoryBase<IncidentStep, UUID> {

  private static final Sort CHRONOLOGICAL = Sort.ascending("startedAt").and("id");

  /** Steps of one incident in the order they ran. */
  public List<IncidentStep> listByIncident(UUID incidentId) {
    return list("incidentId", CHRONOLOGICAL, incidentId);
  }

  /** The most recent step of this type for the incident. */
  public Optional<IncidentStep> findLatest(UUID incidentId, StepType stepType) {
    return find(
            "incidentId = ?1 and stepType = ?2",
            Sort.descending("startedAt").and("id", Sort.Direction.Descending),
            incidentId,
            stepType)
        .firstResultOptional();
  }
}
