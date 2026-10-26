package dev.mfa.incident.mapper;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.mfa.incident.dto.ModelUsageDto;
import dev.mfa.incident.dto.ReasonCode;
import dev.mfa.incident.dto.StepDto;
import dev.mfa.incident.dto.StepResultDto;
import dev.mfa.incident.dto.TriageResult;
import dev.mfa.incident.model.IncidentState;
import dev.mfa.incident.model.IncidentStep;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.UUID;

@ApplicationScoped
public class IncidentStepMapper {

  private final ObjectMapper json;

  public IncidentStepMapper(ObjectMapper objectMapper) {
    this.json = objectMapper.copy().enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
  }

  /**
   * The typed output of a triage step, as the step stored it.
   *
   * @throws IllegalStateException if the stored output is not a triage result
   */
  public TriageResult toTriageResult(String output) {
    try {
      return json.readValue(output, TriageResult.class);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("Stored triage output is not a triage result", e);
    }
  }

  /**
   * @param reasonCode reason of the transition; {@code null} when the target state needs none
   */
  public IncidentStep toEntity(
      UUID incidentId,
      StepResultDto result,
      IncidentState fromState,
      IncidentState toState,
      ReasonCode reasonCode) {
    IncidentStep step = new IncidentStep();
    step.setIncidentId(incidentId);
    step.setStepType(result.type());
    step.setStatus(result.status());
    step.setFromState(fromState);
    step.setToState(toState);
    step.setInput(result.input());
    step.setOutput(result.output());
    ModelUsageDto usage = result.modelUsage();
    if (usage != null) {
      step.setModelId(usage.modelId());
      step.setPromptId(usage.promptId());
      step.setPromptVersion(usage.promptVersion());
      step.setInputTokens(usage.inputTokens());
      step.setOutputTokens(usage.outputTokens());
    }
    step.setStartedAt(result.startedAt());
    step.setFinishedAt(result.finishedAt());
    step.setDurationMs(result.duration().toMillis());
    step.setReasonCode(reasonCode == null ? null : reasonCode.value());
    step.setErrorMessage(result.errorMessage());
    return step;
  }

  public StepDto toDto(IncidentStep step) {
    return new StepDto(
        step.getId(),
        step.getStepType(),
        step.getStatus(),
        step.getFromState(),
        step.getToState(),
        step.getStartedAt(),
        step.getFinishedAt(),
        step.getDurationMs(),
        step.getReasonCode(),
        step.getErrorMessage(),
        step.getModelId(),
        step.getPromptId(),
        step.getPromptVersion(),
        step.getInputTokens(),
        step.getOutputTokens(),
        step.getOutput());
  }
}
