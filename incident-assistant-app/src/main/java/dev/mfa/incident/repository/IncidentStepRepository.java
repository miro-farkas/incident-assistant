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

  /** Input and output tokens of all steps of the incident; unknown counts as zero. */
  public long sumTokens(UUID incidentId) {
    return getEntityManager()
        .createQuery(
            "select coalesce(sum(coalesce(s.inputTokens, 0) + coalesce(s.outputTokens, 0)), 0)"
                + " from IncidentStep s where s.incidentId = :incident",
            Long.class)
        .setParameter("incident", incidentId)
        .getSingleResult();
  }
}
