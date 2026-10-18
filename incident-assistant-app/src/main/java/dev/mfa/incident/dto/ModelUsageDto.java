package dev.mfa.incident.dto;

import static dev.mfa.incident.dto.Validation.requireNonNegative;
import static dev.mfa.incident.dto.Validation.requireText;

/** Model and prompt that produced a step result, with token usage (ADR-0003, ADR-0008). */
public record ModelUsageDto(
    String modelId, String promptId, String promptVersion, int inputTokens, int outputTokens) {

  public static final int MAX_MODEL_ID_LENGTH = 200;
  public static final int MAX_PROMPT_ID_LENGTH = 100;
  public static final int MAX_PROMPT_VERSION_LENGTH = 32;

  public ModelUsageDto {
    requireText(modelId, "modelId", MAX_MODEL_ID_LENGTH);
    requireText(promptId, "promptId", MAX_PROMPT_ID_LENGTH);
    requireText(promptVersion, "promptVersion", MAX_PROMPT_VERSION_LENGTH);
    requireNonNegative(inputTokens, "inputTokens");
    requireNonNegative(outputTokens, "outputTokens");
  }
}
