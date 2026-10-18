package dev.mfa.incident.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * One executed step with the transition it caused. Rows are never updated (audit trail): the
 * repository only inserts them. Not {@code @Immutable}, because Hibernate cannot apply the
 * {@code @Version} write lock to read-only entities.
 */
@Entity
@Table(name = "incident_step")
public class IncidentStep extends BaseEntity {

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

  public UUID getIncidentId() {
    return incidentId;
  }

  public void setIncidentId(UUID incidentId) {
    this.incidentId = incidentId;
  }

  public StepType getStepType() {
    return stepType;
  }

  public void setStepType(StepType stepType) {
    this.stepType = stepType;
  }

  public StepStatus getStatus() {
    return status;
  }

  public void setStatus(StepStatus status) {
    this.status = status;
  }

  public IncidentState getFromState() {
    return fromState;
  }

  public void setFromState(IncidentState fromState) {
    this.fromState = fromState;
  }

  public IncidentState getToState() {
    return toState;
  }

  public void setToState(IncidentState toState) {
    this.toState = toState;
  }

  public String getInput() {
    return input;
  }

  public void setInput(String input) {
    this.input = input;
  }

  public String getOutput() {
    return output;
  }

  public void setOutput(String output) {
    this.output = output;
  }

  public String getModelId() {
    return modelId;
  }

  public void setModelId(String modelId) {
    this.modelId = modelId;
  }

  public String getPromptId() {
    return promptId;
  }

  public void setPromptId(String promptId) {
    this.promptId = promptId;
  }

  public String getPromptVersion() {
    return promptVersion;
  }

  public void setPromptVersion(String promptVersion) {
    this.promptVersion = promptVersion;
  }

  public Integer getInputTokens() {
    return inputTokens;
  }

  public void setInputTokens(Integer inputTokens) {
    this.inputTokens = inputTokens;
  }

  public Integer getOutputTokens() {
    return outputTokens;
  }

  public void setOutputTokens(Integer outputTokens) {
    this.outputTokens = outputTokens;
  }

  public Instant getStartedAt() {
    return startedAt;
  }

  public void setStartedAt(Instant startedAt) {
    this.startedAt = startedAt;
  }

  public Instant getFinishedAt() {
    return finishedAt;
  }

  public void setFinishedAt(Instant finishedAt) {
    this.finishedAt = finishedAt;
  }

  public long getDurationMs() {
    return durationMs;
  }

  public void setDurationMs(long durationMs) {
    this.durationMs = durationMs;
  }

  public String getReasonCode() {
    return reasonCode;
  }

  public void setReasonCode(String reasonCode) {
    this.reasonCode = reasonCode;
  }

  public String getErrorMessage() {
    return errorMessage;
  }

  public void setErrorMessage(String errorMessage) {
    this.errorMessage = errorMessage;
  }
}
