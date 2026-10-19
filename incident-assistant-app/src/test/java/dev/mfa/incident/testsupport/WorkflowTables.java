package dev.mfa.incident.testsupport;

import dev.mfa.incident.model.OutboxEvent;
import io.quarkus.narayana.jta.QuarkusTransaction;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/** Reads committed workflow rows directly, so tests check what the database really holds. */
@ApplicationScoped
public class WorkflowTables {

  /** Columns of one {@code incident_step} row; JSON columns as text. */
  public record StepRow(
      UUID id,
      Instant createdAt,
      Instant updatedAt,
      long version,
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
                        select id, created_at, updated_at, version, step_type, status,
                               from_state, to_state, input::text, output::text, model_id,
                               prompt_id, prompt_version, input_tokens, output_tokens,
                               duration_ms, reason_code, error_message
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

  /** Outbox events of one incident in relay order. */
  public List<OutboxEvent> outboxEvents(UUID incidentId) {
    return QuarkusTransaction.requiringNew()
        .call(
            () ->
                entityManager
                    .createQuery(
                        "select e from OutboxEvent e where e.messageKey = :key"
                            + " order by e.sequenceNumber",
                        OutboxEvent.class)
                    .setParameter("key", incidentId.toString())
                    .getResultList());
  }

  /** Ids of all incidents, open or closed, for one alert, oldest first. */
  public List<UUID> incidentIds(String source, String fingerprint) {
    return QuarkusTransaction.requiringNew()
        .call(
            () ->
                entityManager
                    .createNativeQuery(
                        "select id from incident where source = :source"
                            + " and fingerprint = :fingerprint order by created_at, id",
                        UUID.class)
                    .setParameter("source", source)
                    .setParameter("fingerprint", fingerprint)
                    .getResultList()
                    .stream()
                    .map(UUID.class::cast)
                    .toList());
  }

  /** Alert payloads of the occurrences of one incident as text, oldest first. */
  public List<String> occurrencePayloads(UUID incidentId) {
    return QuarkusTransaction.requiringNew()
        .call(
            () ->
                entityManager
                    .createNativeQuery(
                        "select alert_payload::text from alert_occurrence"
                            + " where incident_id = :incidentId order by created_at, id",
                        String.class)
                    .setParameter("incidentId", incidentId)
                    .getResultList()
                    .stream()
                    .map(String.class::cast)
                    .toList());
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
        instant(row[1]),
        instant(row[2]),
        ((Number) row[3]).longValue(),
        (String) row[4],
        (String) row[5],
        (String) row[6],
        (String) row[7],
        (String) row[8],
        (String) row[9],
        (String) row[10],
        (String) row[11],
        (String) row[12],
        (Integer) row[13],
        (Integer) row[14],
        ((Number) row[15]).longValue(),
        (String) row[16],
        (String) row[17]);
  }

  private static Instant instant(Object value) {
    return switch (value) {
      case Instant instant -> instant;
      case OffsetDateTime offsetDateTime -> offsetDateTime.toInstant();
      case Timestamp timestamp -> timestamp.toInstant();
      default -> throw new IllegalStateException("Unexpected timestamp type " + value.getClass());
    };
  }
}
