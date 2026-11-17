package dev.mfa.incident.dto;

import dev.mfa.incident.model.StepStatus;
import dev.mfa.incident.model.StepType;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * Outcome of one executed workflow step, recorded as an {@code incident_step} row.
 *
 * @param input JSON document the step worked on
 * @param output JSON document the step produced; {@code null} when it produced nothing
 * @param modelUsage model, prompt and tokens; {@code null} for steps without an LLM call
 * @param errorMessage diagnostic message for a failed step; must not contain PII or prompt text.
 *     Longer messages are truncated to {@link #MAX_ERROR_MESSAGE_LENGTH}.
 * @param toolCalls the tool calls of an AI step that may use tools, in order (A-0015); {@code null}
 *     for steps without tools
 */
public record StepResultDto(
    StepType type,
    StepStatus status,
    String input,
    String output,
    ModelUsageDto modelUsage,
    Instant startedAt,
    Instant finishedAt,
    String errorMessage,
    List<ToolCallDto> toolCalls) {

  public static final int MAX_ERROR_MESSAGE_LENGTH = 2000;

  public StepResultDto {
    Objects.requireNonNull(type, "type");
    Objects.requireNonNull(status, "status");
    Objects.requireNonNull(input, "input");
    Objects.requireNonNull(startedAt, "startedAt");
    Objects.requireNonNull(finishedAt, "finishedAt");
    if (finishedAt.isBefore(startedAt)) {
      throw new IllegalArgumentException("finishedAt must not be before startedAt");
    }
    if (status == StepStatus.FAILED && (errorMessage == null || errorMessage.isBlank())) {
      throw new IllegalArgumentException("A failed step requires an errorMessage");
    }
    if (errorMessage != null && errorMessage.length() > MAX_ERROR_MESSAGE_LENGTH) {
      errorMessage = errorMessage.substring(0, MAX_ERROR_MESSAGE_LENGTH);
    }
    toolCalls = toolCalls == null ? null : List.copyOf(toolCalls);
  }

  /** A step that uses no tools. */
  public StepResultDto(
      StepType type,
      StepStatus status,
      String input,
      String output,
      ModelUsageDto modelUsage,
      Instant startedAt,
      Instant finishedAt,
      String errorMessage) {
    this(type, status, input, output, modelUsage, startedAt, finishedAt, errorMessage, null);
  }

  public Duration duration() {
    return Duration.between(startedAt, finishedAt);
  }
}
