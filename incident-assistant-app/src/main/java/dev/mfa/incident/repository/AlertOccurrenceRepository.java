package dev.mfa.incident.repository;

import dev.mfa.incident.model.AlertOccurrence;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/** Append-only: services only insert occurrence rows. */
@ApplicationScoped
public class AlertOccurrenceRepository implements PanacheRepositoryBase<AlertOccurrence, UUID> {

  public long countByIncident(UUID incidentId) {
    return count("incidentId", incidentId);
  }

  /**
   * Occurrences per incident in one grouped query (index {@code ix_alert_occurrence_incident});
   * incidents without occurrences are absent from the result.
   */
  public Map<UUID, Long> countByIncidents(Collection<UUID> incidentIds) {
    if (incidentIds.isEmpty()) {
      return Map.of();
    }
    return getEntityManager()
        .createQuery(
            "select o.incidentId, count(o) from AlertOccurrence o"
                + " where o.incidentId in :ids group by o.incidentId",
            Object[].class)
        .setParameter("ids", incidentIds)
        .getResultStream()
        .collect(Collectors.toMap(row -> (UUID) row[0], row -> (Long) row[1]));
  }
}
