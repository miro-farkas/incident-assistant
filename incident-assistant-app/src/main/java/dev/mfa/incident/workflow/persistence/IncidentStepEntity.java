package dev.mfa.incident.workflow.persistence;

import dev.mfa.incident.workflow.IncidentState;
import dev.mfa.incident.workflow.ModelUsage;
import dev.mfa.incident.workflow.ReasonCode;
import dev.mfa.incident.workflow.StepResult;
import dev.mfa.incident.workflow.StepStatus;
import dev.mfa.incident.workflow.StepType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UuidGenerator;
import org.hibernate.type.SqlTypes;

/** One executed step with the transition it caused. Rows are never updated (audit trail). */
@Entity
@Immutable
@Table(name = "incident_step")
public class IncidentStepEntity {

  @Id
  @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
  private UUID id;

  @Column(name = "incident_id", nullable = false)
  private UUID incidentId;

  @Enumerated(EnumType.STRING)
  @Column(name = "step_type", nullable = false, length = 32)
  private StepType stepType;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 16)
  private StepStatus status;

  @Enumerated(EnumType.STRING)
  @Column(name = "from_state", nullable = false, length = 32)
  private IncidentState fromState;

  @Enumerated(EnumType.STRING)
  @Column(name = "to_state", nullable = false, length = 32)
  private IncidentState toState;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "input", nullable = false)
  private String input;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "output")
  private String output;

  @Column(name = "model_id", length = 200)
  private String modelId;

  @Column(name = "prompt_id", length = 100)
  private String promptId;

  @Column(name = "prompt_version", length = 32)
  private String promptVersion;

  @Column(name = "input_tokens")
  private Integer inputTokens;

  @Column(name = "output_tokens")
  private Integer outputTokens;

  @Column(name = "started_at", nullable = false)
  private Instant startedAt;

  @Column(name = "finished_at", nullable = false)
  private Instant finishedAt;

  @Column(name = "duration_ms", nullable = false)
  private long durationMs;

  @Column(name = "reason_code", length = 64)
  private String reasonCode;

  @Column(name = "error_message", length = 2000)
  private String errorMessage;

  protected IncidentStepEntity() {}

  public static IncidentStepEntity recorded(
      UUID incidentId,
      StepResult result,
      IncidentState fromState,
      IncidentState toState,
      ReasonCode reasonCode) {
    IncidentStepEntity entity = new IncidentStepEntity();
    entity.incidentId = incidentId;
    entity.stepType = result.type();
    entity.status = result.status();
    entity.fromState = fromState;
    entity.toState = toState;
    entity.input = result.input();
    entity.output = result.output();
    ModelUsage usage = result.modelUsage();
    if (usage != null) {
      entity.modelId = usage.modelId();
      entity.promptId = usage.promptId();
      entity.promptVersion = usage.promptVersion();
      entity.inputTokens = usage.inputTokens();
      entity.outputTokens = usage.outputTokens();
    }
    entity.startedAt = result.startedAt();
    entity.finishedAt = result.finishedAt();
    entity.durationMs = result.duration().toMillis();
    entity.reasonCode = reasonCode == null ? null : reasonCode.value();
    entity.errorMessage = result.errorMessage();
    return entity;
  }

  public UUID id() {
    return id;
  }
}
