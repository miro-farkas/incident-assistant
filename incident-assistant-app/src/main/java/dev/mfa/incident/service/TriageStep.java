package dev.mfa.incident.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.langchain4j.guardrail.OutputGuardrailException;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.output.TokenUsage;
import dev.langchain4j.service.Result;
import dev.mfa.incident.ai.PromptVersions;
import dev.mfa.incident.ai.TriageAssistant;
import dev.mfa.incident.config.WorkflowConfig;
import dev.mfa.incident.dto.ClaimedIncidentDto;
import dev.mfa.incident.dto.ModelUsageDto;
import dev.mfa.incident.dto.ReasonCode;
import dev.mfa.incident.dto.StepResultDto;
import dev.mfa.incident.dto.TriageResult;
import dev.mfa.incident.exception.IncidentVersionConflictException;
import dev.mfa.incident.model.IncidentState;
import dev.mfa.incident.model.StepStatus;
import dev.mfa.incident.model.StepType;
import jakarta.enterprise.context.ApplicationScoped;
import java.time.Instant;
import org.jboss.logging.Logger;

/**
 * The triage step (A-0001, A-0014): asks {@link TriageAssistant} about the alert of an incident in
 * {@code RECEIVED} and records the outcome as one step row. A valid result moves the incident to
 * {@code TRIAGED}; an answer still invalid after the reprompt, an unavailable model or an exceeded
 * token budget escalates it.
 */
@ApplicationScoped
public class TriageStep implements WorkflowStep {

  public static final ReasonCode TRIAGE_INVALID_OUTPUT = new ReasonCode("TRIAGE_INVALID_OUTPUT");
  public static final ReasonCode TRIAGE_UNAVAILABLE = new ReasonCode("TRIAGE_UNAVAILABLE");

  private static final Logger LOG = Logger.getLogger(TriageStep.class);

  private final TriageAssistant assistant;
  private final IncidentService incidents;
  private final TokenBudget budget;
  private final ObjectMapper json;
  private final String configuredModel;
  private final PromptVersions prompt = PromptVersions.of("triage");

  public TriageStep(
      TriageAssistant assistant,
      IncidentService incidents,
      TokenBudget budget,
      ObjectMapper json,
      WorkflowConfig config) {
    this.assistant = assistant;
    this.incidents = incidents;
    this.budget = budget;
    this.json = json;
    this.configuredModel = config.triageModel();
  }

  @Override
  public IncidentState sourceState() {
    return IncidentStateMachine.sourceState(StepType.TRIAGE);
  }

  @Override
  public void run(ClaimedIncidentDto incident) {
    Instant started = Instant.now();
    ObjectNode input = AlertInput.of(json, incident.alertPayload());
    Outcome outcome = triage(incident, input);
    StepResultDto result =
        new StepResultDto(
            StepType.TRIAGE,
            outcome.status(),
            input.toString(),
            outcome.output(),
            outcome.usage(),
            started,
            Instant.now(),
            outcome.error());
    try {
      incidents.recordStep(
          incident.id(), incident.version(), result, outcome.target(), outcome.reason());
      LOG.infof("Triage of incident %s: %s", incident.id(), outcome.target());
    } catch (IncidentVersionConflictException e) {
      LOG.infof("Triage of incident %s discarded: the incident changed meanwhile", incident.id());
    }
  }

  private Outcome triage(ClaimedIncidentDto incident, ObjectNode input) {
    Result<TriageResult> answer;
    try {
      answer =
          assistant.triage(
              AlertInput.text(input, "alertName"),
              AlertInput.text(input, "source"),
              AlertInput.text(input, "service"),
              AlertInput.text(input, "severity"),
              AlertInput.text(input, "summary"),
              AlertInput.text(input, "description"),
              input.path("labels").toString());
    } catch (OutputGuardrailException e) {
      return Outcome.failed(
          TRIAGE_INVALID_OUTPUT,
          "The model's answer was still invalid after the reprompt: " + e.getMessage(),
          usage(null, null));
    } catch (RuntimeException e) {
      LOG.warnf("Triage of incident %s failed (%s)", incident.id(), e.getClass().getSimpleName());
      return Outcome.failed(
          TRIAGE_UNAVAILABLE,
          "The model could not be reached in time: " + e.getClass().getSimpleName(),
          usage(null, null));
    }
    ModelUsageDto usage = usage(answer.finalResponse(), answer.tokenUsage());
    String output = toJson(answer.content());
    if (budget.exceededWith(incident.id(), usage)) {
      return new Outcome(
          StepStatus.FAILED,
          IncidentState.ESCALATED,
          TokenBudget.TOKEN_BUDGET_EXCEEDED,
          output,
          usage,
          "Token budget of " + budget.limit() + " exceeded");
    }
    return new Outcome(StepStatus.SUCCEEDED, IncidentState.TRIAGED, null, output, usage, null);
  }

  private ModelUsageDto usage(ChatResponse response, TokenUsage tokens) {
    String model =
        response == null || response.modelName() == null || response.modelName().isBlank()
            ? configuredModel
            : response.modelName();
    return new ModelUsageDto(
        model,
        prompt.id(),
        prompt.version(),
        tokens == null ? null : tokens.inputTokenCount(),
        tokens == null ? null : tokens.outputTokenCount());
  }

  private String toJson(TriageResult result) {
    try {
      return json.writeValueAsString(result);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("Triage result not serialisable", e);
    }
  }

  private record Outcome(
      StepStatus status,
      IncidentState target,
      ReasonCode reason,
      String output,
      ModelUsageDto usage,
      String error) {

    static Outcome failed(ReasonCode reason, String error, ModelUsageDto usage) {
      return new Outcome(StepStatus.FAILED, IncidentState.ESCALATED, reason, null, usage, error);
    }
  }
}
