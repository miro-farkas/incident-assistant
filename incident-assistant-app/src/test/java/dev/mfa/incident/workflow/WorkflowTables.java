package dev.mfa.incident.workflow;

import dev.mfa.incident.outbox.persistence.OutboxEventEntity;
import io.quarkus.narayana.jta.QuarkusTransaction;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.UUID;

/** Reads committed workflow rows directly, so tests check what the database really holds. */
@ApplicationScoped
public class WorkflowTables {

  /** Columns of one {@code incident_step} row; JSON columns as text. */
  public record StepRow(
      UUID id,
      String stepType,
      String status,
      String fromState,
      String toState,
      String input,
      String output,
      String modelId,
      String promptId,
      String promptVersion,
      Integer inputTokens,
      Integer outputTokens,
      long durationMs,
      String reasonCode,
      String errorMessage) {}

  private final EntityManager entityManager;

  public WorkflowTables(EntityManager entityManager) {
    this.entityManager = entityManager;
  }

  public List<StepRow> steps(UUID incidentId) {
    return QuarkusTransaction.requiringNew()
        .call(
            () ->
                entityManager
                    .createNativeQuery(
                        """
                        select id, step_type, status, from_state, to_state, input::text,
                               output::text, model_id, prompt_id, prompt_version, input_tokens,
                               output_tokens, duration_ms, reason_code, error_message
                        from incident_step where incident_id = :incidentId
                        order by started_at, id
                        """,
                        Object[].class)
                    .setParameter("incidentId", incidentId)
                    .getResultList()
                    .stream()
                    .map(row -> toStepRow((Object[]) row))
                    .toList());
  }

  public List<OutboxEventEntity> outboxEvents(UUID incidentId) {
    return QuarkusTransaction.requiringNew()
        .call(
            () ->
                entityManager
                    .createQuery(
                        "select e from OutboxEventEntity e where e.messageKey = :key order by e.id",
                        OutboxEventEntity.class)
                    .setParameter("key", incidentId.toString())
                    .getResultList());
  }

  public String alertPayload(UUID incidentId) {
    return QuarkusTransaction.requiringNew()
        .call(
            () ->
                (String)
                    entityManager
                        .createNativeQuery(
                            "select alert_payload::text from incident where id = :id")
                        .setParameter("id", incidentId)
                        .getSingleResult());
  }

  private static StepRow toStepRow(Object[] row) {
    return new StepRow(
        (UUID) row[0],
        (String) row[1],
        (String) row[2],
        (String) row[3],
        (String) row[4],
        (String) row[5],
        (String) row[6],
        (String) row[7],
        (String) row[8],
        (String) row[9],
        (Integer) row[10],
        (Integer) row[11],
        ((Number) row[12]).longValue(),
        (String) row[13],
        (String) row[14]);
  }
}
