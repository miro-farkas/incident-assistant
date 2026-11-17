package dev.mfa.incident.service;

import static dev.mfa.incident.testsupport.WorkflowFixtures.alert;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.guardrail.OutputGuardrailException;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.output.TokenUsage;
import dev.langchain4j.service.Result;
import dev.mfa.incident.ai.DiagnosisAgent;
import dev.mfa.incident.ai.ToolCallBudgetExceededException;
import dev.mfa.incident.ai.TriageAssistant;
import dev.mfa.incident.dto.Diagnosis;
import dev.mfa.incident.dto.TriageResult;
import dev.mfa.incident.model.IncidentState;
import dev.mfa.incident.testsupport.ExecutorProfile;
import dev.mfa.incident.testsupport.WorkflowTables;
import dev.mfa.incident.testsupport.WorkflowTables.StepRow;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.eclipse.microprofile.faulttolerance.exceptions.TimeoutException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * The diagnosis step records one outcome per triaged incident (A-0015), with mocked AI services
 * (P-0010). The agent loop with its tools is covered by {@code DiagnosisAgentLoopTest}.
 */
@QuarkusTest
@TestProfile(ExecutorProfile.class)
class DiagnosisStepTest {

  private static final Duration TIMEOUT = Duration.ofSeconds(20);

  private static final TriageResult TRIAGE =
      new TriageResult(
          TriageResult.Severity.HIGH,
          TriageResult.Category.DEPLOYMENT,
          "checkout",
          "Checkout fails for many customers since the last release.");

  private static final Diagnosis DIAGNOSIS =
      new Diagnosis(
          "Release 2026.10.18-1 of checkout broke the payment call.",
          Diagnosis.Confidence.HIGH,
          List.of(
              new Diagnosis.Evidence(
                  "Errors started with the deployment.",
                  Diagnosis.SourceType.TOOL,
                  "get_recent_deployments")));

  @InjectMock TriageAssistant triage;
  @InjectMock DiagnosisAgent agent;

  @Inject AlertIngestionService ingestion;
  @Inject IncidentService incidents;
  @Inject WorkflowTables tables;
  @Inject ObjectMapper json;

  @BeforeEach
  void triageSucceeds() {
    when(triage.triage(any(), any(), any(), any(), any(), any(), any()))
        .thenReturn(answer(TRIAGE, 100, 20));
  }

  @Test
  void validDiagnosisMovesTheIncidentToDiagnosedAndRecordsTheCall() throws Exception {
    when(diagnose()).thenReturn(answer(DIAGNOSIS, 4_200, 310));

    UUID id = ingestion.ingest(alert()).incidentId();

    awaitState(id, IncidentState.DIAGNOSED);
    StepRow step = diagnosisStep(id);
    assertThat(step.status()).isEqualTo("SUCCEEDED");
    assertThat(step.fromState()).isEqualTo("TRIAGED");
    assertThat(step.reasonCode()).isNull();
    assertThat(step.modelId()).isEqualTo("qwen2.5:3b");
    assertThat(step.promptId()).isEqualTo("diagnosis");
    assertThat(step.promptVersion()).isEqualTo("1");
    assertThat(step.inputTokens()).isEqualTo(4_200);
    assertThat(step.outputTokens()).isEqualTo(310);
    assertThat(json.readValue(step.output(), Diagnosis.class)).isEqualTo(DIAGNOSIS);
    assertThat(json.readTree(step.toolCalls()).isArray()).isTrue();
    JsonNode input = json.readTree(step.input());
    assertThat(input.path("service").asText()).isEqualTo("checkout");
    assertThat(input.path("triage").path("category").asText()).isEqualTo("DEPLOYMENT");
    assertThat(input.has("generatorUrl")).isFalse();
    verify(agent)
        .diagnose(
            eq(6),
            eq("HighErrorRate"),
            eq("prometheus"),
            eq("checkout"),
            eq("critical"),
            any(),
            any(),
            any(),
            eq("HIGH"),
            eq("DEPLOYMENT"),
            eq("checkout"),
            eq(TRIAGE.summary()));
    assertThat(tables.lease(id).owner()).as("lease released").isNull();
  }

  @Test
  void answerStillInvalidAfterTheRepromptEscalates() {
    when(diagnose())
        .thenThrow(new OutputGuardrailException("Evidence cites sources this diagnosis did not"));

    UUID id = ingestion.ingest(alert()).incidentId();

    awaitState(id, IncidentState.ESCALATED);
    StepRow step = diagnosisStep(id);
    assertThat(step.status()).isEqualTo("FAILED");
    assertThat(step.reasonCode()).isEqualTo("DIAGNOSIS_INVALID_OUTPUT");
    assertThat(step.errorMessage()).contains("Evidence cites sources");
    assertThat(step.output()).isNull();
    assertThat(step.modelId()).isEqualTo("ollama/qwen2.5:3b");
    assertThat(step.promptId()).isEqualTo("diagnosis");
  }

  @Test
  void timeoutEscalates() {
    when(diagnose()).thenThrow(new TimeoutException("timed out"));

    UUID id = ingestion.ingest(alert()).incidentId();

    awaitState(id, IncidentState.ESCALATED);
    assertThat(diagnosisStep(id).reasonCode()).isEqualTo("DIAGNOSIS_TIMEOUT");
  }

  @Test
  void callBeyondTheToolBudgetEscalates() {
    when(diagnose()).thenThrow(new ToolCallBudgetExceededException(6));

    UUID id = ingestion.ingest(alert()).incidentId();

    awaitState(id, IncidentState.ESCALATED);
    StepRow step = diagnosisStep(id);
    assertThat(step.reasonCode()).isEqualTo("DIAGNOSIS_TOOL_BUDGET_EXCEEDED");
    assertThat(step.errorMessage()).isEqualTo("More than 6 tool calls requested");
  }

  @Test
  void unreachableModelOrToolsEscalate() {
    when(diagnose()).thenThrow(new RuntimeException("Failed to retrieve tools from MCP server"));

    UUID id = ingestion.ingest(alert()).incidentId();

    awaitState(id, IncidentState.ESCALATED);
    StepRow step = diagnosisStep(id);
    assertThat(step.reasonCode()).isEqualTo("DIAGNOSIS_UNAVAILABLE");
    assertThat(step.errorMessage()).doesNotContain("MCP server");
  }

  @Test
  void exceedingTheTokenBudgetEscalatesAndKeepsTheOutput() {
    when(diagnose()).thenReturn(answer(DIAGNOSIS, 55_000, 6_000));

    UUID id = ingestion.ingest(alert()).incidentId();

    awaitState(id, IncidentState.ESCALATED);
    StepRow step = diagnosisStep(id);
    assertThat(step.status()).isEqualTo("FAILED");
    assertThat(step.reasonCode()).isEqualTo("TOKEN_BUDGET_EXCEEDED");
    assertThat(step.output()).isNotNull();
    assertThat(step.inputTokens()).isEqualTo(55_000);
  }

  private Result<Diagnosis> diagnose() {
    return agent.diagnose(
        anyInt(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any());
  }

  private StepRow diagnosisStep(UUID id) {
    return tables.steps(id).stream()
        .filter(step -> step.stepType().equals("DIAGNOSIS"))
        .reduce((first, second) -> second)
        .orElseThrow();
  }

  private void awaitState(UUID id, IncidentState state) {
    await().atMost(TIMEOUT).until(() -> incidents.get(id).state() == state);
  }

  private static <T> Result<T> answer(T content, int input, int output) {
    return Result.<T>builder()
        .content(content)
        .tokenUsage(new TokenUsage(input, output))
        .finalResponse(
            ChatResponse.builder().aiMessage(AiMessage.from("{}")).modelName("qwen2.5:3b").build())
        .build();
  }
}
