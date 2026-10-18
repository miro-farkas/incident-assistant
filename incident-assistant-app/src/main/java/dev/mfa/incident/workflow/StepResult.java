package dev.mfa.incident.workflow;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/**
 * Outcome of one executed workflow step, recorded as an {@code incident_step} row.
 *
 * @param input JSON document the step worked on
 * @param output JSON document the step produced; {@code null} when it produced nothing
 * @param modelUsage model, prompt and tokens; {@code null} for steps without an LLM call
 * @param errorMessage diagnostic message for a failed step; must not contain PII or prompt text.
 *     Longer messages are truncated to {@link #MAX_ERROR_MESSAGE_LENGTH}.
 */
public record StepResult(
    StepType type,
    StepStatus status,
    String input,
    String output,
    ModelUsage modelUsage,
    Instant startedAt,
    Instant finishedAt,
    String errorMessage) {

  public static final int MAX_ERROR_MESSAGE_LENGTH = 2000;

  public StepResult {
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
  }

  public Duration duration() {
    return Duration.between(startedAt, finishedAt);
  }
}
