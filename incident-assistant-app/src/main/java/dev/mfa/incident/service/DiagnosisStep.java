package dev.mfa.incident.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.langchain4j.guardrail.OutputGuardrailException;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.output.TokenUsage;
import dev.langchain4j.service.Result;
import dev.mfa.incident.ai.DiagnosisAgent;
import dev.mfa.incident.ai.DiagnosisRun;
import dev.mfa.incident.ai.PromptVersions;
import dev.mfa.incident.ai.ToolCallBudgetExceededException;
import dev.mfa.incident.config.WorkflowConfig;
import dev.mfa.incident.dto.ClaimedIncidentDto;
import dev.mfa.incident.dto.Diagnosis;
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
import org.eclipse.microprofile.faulttolerance.exceptions.TimeoutException;
import org.jboss.logging.Logger;

/**
 * The diagnosis step (A-0001, A-0015): lets {@link DiagnosisAgent} investigate an incident in
 * {@code TRIAGED} with its read tools and records the outcome as one step row with the tool call
 * trace. A valid diagnosis within the budgets moves the incident to {@code DIAGNOSED}; everything
 * else escalates it.
 */
@ApplicationScoped
public class DiagnosisStep implements WorkflowStep {

  public static final ReasonCode DIAGNOSIS_INVALID_OUTPUT =
      new ReasonCode("DIAGNOSIS_INVALID_OUTPUT");
  public static final ReasonCode DIAGNOSIS_UNAVAILABLE = new ReasonCode("DIAGNOSIS_UNAVAILABLE");
  public static final ReasonCode DIAGNOSIS_TIMEOUT = new ReasonCode("DIAGNOSIS_TIMEOUT");
  public static final ReasonCode DIAGNOSIS_TOOL_BUDGET_EXCEEDED =
      new ReasonCode("DIAGNOSIS_TOOL_BUDGET_EXCEEDED");

  private static final Logger LOG = Logger.getLogger(DiagnosisStep.class);

  private final DiagnosisAgent agent;
  private final IncidentService incidents;
  private final IncidentQueryService queries;
  private final TokenBudget budget;
  private final ObjectMapper json;
  private final String configuredModel;
  private final int maxToolCalls;
  private final PromptVersions prompt = PromptVersions.of("diagnosis");

  public DiagnosisStep(
      DiagnosisAgent agent,
      IncidentService incidents,
      IncidentQueryService queries,
      TokenBudget budget,
      ObjectMapper json,
      WorkflowConfig config) {
    this.agent = agent;
    this.incidents = incidents;
    this.queries = queries;
    this.budget = budget;
    this.json = json;
    this.configuredModel = config.diagnosisModel();
    this.maxToolCalls = config.diagnosis().maxToolCalls();
  }

  @Override
  public IncidentState sourceState() {
    return IncidentStateMachine.sourceState(StepType.DIAGNOSIS);
  }

  /**
   * @throws IllegalStateException if the incident has no successful triage, which a {@code TRIAGED}
   *     incident always has
   */
  @Override
  public void run(ClaimedIncidentDto incident) {
    Instant started = Instant.now();
    TriageResult triage =
        queries
            .triageOf(incident.id())
            .orElseThrow(
                () -> new IllegalStateException("Incident " + incident.id() + " has no triage"));
    ObjectNode input = AlertInput.of(json, incident.alertPayload());
    input.set("triage", json.valueToTree(triage));
    DiagnosisRun run = new DiagnosisRun(maxToolCalls, triage.affectedService());
    Outcome outcome = diagnose(incident, input, triage, run);
    StepResultDto result =
        new StepResultDto(
            StepType.DIAGNOSIS,
            outcome.status(),
            input.toString(),
            outcome.output(),
            outcome.usage(),
            started,
            Instant.now(),
            outcome.error(),
            run.toolCalls());
    try {
      incidents.recordStep(
          incident.id(), incident.version(), result, outcome.target(), outcome.reason());
      LOG.infof(
          "Diagnosis of incident %s: %s after %d tool calls",
          incident.id(), outcome.target(), run.toolCalls().size());
    } catch (IncidentVersionConflictException e) {
      LOG.infof(
          "Diagnosis of incident %s discarded: the incident changed meanwhile", incident.id());
    }
  }

  private Outcome diagnose(
      ClaimedIncidentDto incident, ObjectNode input, TriageResult triage, DiagnosisRun run) {
    Result<Diagnosis> answer;
    try {
      answer =
          run.run(
              () ->
                  agent.diagnose(
                      maxToolCalls,
                      AlertInput.text(input, "alertName"),
                      AlertInput.text(input, "source"),
                      AlertInput.text(input, "service"),
                      AlertInput.text(input, "severity"),
                      AlertInput.text(input, "summary"),
                      AlertInput.text(input, "description"),
                      input.path("labels").toString(),
                      triage.severity().name(),
                      triage.category().name(),
                      triage.affectedService(),
                      triage.summary()));
    } catch (ToolCallBudgetExceededException e) {
      return toolBudgetExceeded(null, usage(null, null));
    } catch (OutputGuardrailException e) {
      return Outcome.failed(
          DIAGNOSIS_INVALID_OUTPUT,
          "The model's answer was still invalid after the reprompt: " + e.getMessage(),
          usage(null, null));
    } catch (TimeoutException e) {
      return Outcome.failed(
          DIAGNOSIS_TIMEOUT,
          "The diagnosis did not finish within its time limit",
          usage(null, null));
    } catch (RuntimeException e) {
      if (run.budgetExceeded()) {
        return toolBudgetExceeded(null, usage(null, null));
      }
      LOG.warnf(
          "Diagnosis of incident %s failed (%s)", incident.id(), e.getClass().getSimpleName());
      return Outcome.failed(
          DIAGNOSIS_UNAVAILABLE,
          "The model or the tools could not be reached: " + e.getClass().getSimpleName(),
          usage(null, null));
    }
    ModelUsageDto usage = usage(answer.finalResponse(), answer.tokenUsage());
    String output = toJson(answer.content());
    if (run.budgetExceeded()) {
      return toolBudgetExceeded(output, usage);
    }
    if (budget.exceededWith(incident.id(), usage)) {
      return new Outcome(
          StepStatus.FAILED,
          IncidentState.ESCALATED,
          TokenBudget.TOKEN_BUDGET_EXCEEDED,
          output,
          usage,
          "Token budget of " + budget.limit() + " exceeded");
    }
    return new Outcome(StepStatus.SUCCEEDED, IncidentState.DIAGNOSED, null, output, usage, null);
  }

  private Outcome toolBudgetExceeded(String output, ModelUsageDto usage) {
    return new Outcome(
        StepStatus.FAILED,
        IncidentState.ESCALATED,
        DIAGNOSIS_TOOL_BUDGET_EXCEEDED,
        output,
        usage,
        "More than " + maxToolCalls + " tool calls requested");
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

  private String toJson(Diagnosis diagnosis) {
    try {
      return json.writeValueAsString(diagnosis);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("Diagnosis not serialisable", e);
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
