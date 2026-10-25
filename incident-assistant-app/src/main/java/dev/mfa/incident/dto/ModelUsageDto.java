package dev.mfa.incident.dto;

import static dev.mfa.incident.dto.Validation.requireNonNegative;
import static dev.mfa.incident.dto.Validation.requireText;

/**
 * Model and prompt that produced a step result, with token usage (P-0003, P-0008).
 *
 * @param inputTokens {@code null} when unknown, for example when the call failed
 * @param outputTokens {@code null} when unknown
 */
public record ModelUsageDto(
    String modelId,
    String promptId,
    String promptVersion,
    Integer inputTokens,
    Integer outputTokens) {

  public static final int MAX_MODEL_ID_LENGTH = 200;
  public static final int MAX_PROMPT_ID_LENGTH = 100;
  public static final int MAX_PROMPT_VERSION_LENGTH = 32;

  public ModelUsageDto {
    requireText(modelId, "modelId", MAX_MODEL_ID_LENGTH);
    requireText(promptId, "promptId", MAX_PROMPT_ID_LENGTH);
    requireText(promptVersion, "promptVersion", MAX_PROMPT_VERSION_LENGTH);
    if (inputTokens != null) {
      requireNonNegative(inputTokens, "inputTokens");
    }
    if (outputTokens != null) {
      requireNonNegative(outputTokens, "outputTokens");
    }
  }

  /** Tokens used, counting unknown ones as zero. */
  public long totalTokens() {
    return (inputTokens == null ? 0L : inputTokens) + (outputTokens == null ? 0L : outputTokens);
  }
}
