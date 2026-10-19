package dev.mfa.incident.repository;

import dev.mfa.incident.exception.IncidentVersionConflictException;
import dev.mfa.incident.exception.OpenIncidentExistsException;
import dev.mfa.incident.model.Incident;
import dev.mfa.incident.model.IncidentState;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.OptimisticLockException;
import jakarta.persistence.PersistenceException;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.hibernate.exception.ConstraintViolationException;

@ApplicationScoped
public class IncidentRepository implements PanacheRepositoryBase<Incident, UUID> {

  /** Partial unique index allowing one open incident per source and fingerprint (V1). */
  static final String OPEN_INCIDENT_INDEX = "ux_incident_open_fingerprint";

  /**
   * Returns the incident for this source and fingerprint whose state is not one of {@code
   * closedStates}; the unique index guarantees there is at most one.
   */
  public Optional<Incident> findOpen(
      String source, String fingerprint, Set<IncidentState> closedStates) {
    return find(
            "source = ?1 and fingerprint = ?2 and state not in ?3",
            source,
            fingerprint,
            closedStates)
        .firstResultOptional();
  }

  /**
   * Inserts a new incident immediately, so a competing open incident is reported here instead of at
   * commit.
   *
   * @throws OpenIncidentExistsException if an open incident with the same source and fingerprint
   *     was committed by another transaction
   */
  public void persistNew(Incident incident) {
    try {
      persist(incident);
      flush();
    } catch (PersistenceException e) {
      if (violates(e, OPEN_INCIDENT_INDEX)) {
        throw new OpenIncidentExistsException();
      }
      throw e;
    }
  }

  /**
   * Writes pending changes and reports a concurrent update of the incident as a domain exception,
   * so the new version is known afterwards and the conflict surfaces here instead of at commit.
   *
   * @throws IncidentVersionConflictException if another transaction updated the incident first
   */
  public void flush(Incident incident, long expectedVersion) {
    try {
      flush();
    } catch (OptimisticLockException e) {
      throw new IncidentVersionConflictException(incident.getId(), expectedVersion);
    }
  }

  private static boolean violates(Throwable failure, String constraintName) {
    for (Throwable t = failure; t != null; t = t.getCause()) {
      if (t instanceof ConstraintViolationException violation) {
        return constraintName.equalsIgnoreCase(violation.getConstraintName());
      }
    }
    return false;
  }
}
