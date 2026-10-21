package dev.mfa.incident.repository;

import dev.mfa.incident.exception.IncidentVersionConflictException;
import dev.mfa.incident.model.ApprovalDecision;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.PersistenceException;
import java.util.Optional;
import java.util.UUID;

/** Append-only: services only insert decisions. */
@ApplicationScoped
public class ApprovalDecisionRepository implements PanacheRepositoryBase<ApprovalDecision, UUID> {

  /** Unique constraint allowing one decision per proposal step (V1). */
  static final String ONE_DECISION_PER_PROPOSAL = "uq_approval_decision_proposal";

  /**
   * Inserts a decision immediately, so a decision committed concurrently on the same proposal is
   * reported here instead of at commit.
   *
   * @throws IncidentVersionConflictException if another transaction decided on the proposal first
   */
  public void persistNew(ApprovalDecision decision, long expectedIncidentVersion) {
    try {
      persist(decision);
      flush();
    } catch (PersistenceException e) {
      if (ConstraintViolations.violates(e, ONE_DECISION_PER_PROPOSAL)) {
        throw new IncidentVersionConflictException(
            decision.getIncidentId(), expectedIncidentVersion);
      }
      throw e;
    }
  }

  /** The latest decision on the incident, if any. */
  public Optional<ApprovalDecision> findLatestByIncident(UUID incidentId) {
    return find("incidentId = ?1 order by createdAt desc, id desc", incidentId)
        .firstResultOptional();
  }
}
