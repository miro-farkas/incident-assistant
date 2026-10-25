package dev.mfa.incident.service;

import static dev.mfa.incident.testsupport.WorkflowFixtures.alert;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
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
import dev.mfa.incident.ai.TriageAssistant;
import dev.mfa.incident.dto.AlertMessage;
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
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.eclipse.microprofile.faulttolerance.exceptions.TimeoutException;
import org.junit.jupiter.api.Test;

/**
 * The executor claims new incidents and the triage step records one outcome each (A-0014), with a
 * mocked {@link TriageAssistant} (P-0010).
 */
@QuarkusTest
@TestProfile(ExecutorProfile.class)
class TriageStepTest {

  private static final Duration TIMEOUT = Duration.ofSeconds(20);

  private static final TriageResult RESULT =
      new TriageResult(
          TriageResult.Severity.HIGH,
          TriageResult.Category.DEPLOYMENT,
          "checkout",
          "Checkout fails for many customers since the last release.");

  @InjectMock TriageAssistant assistant;

  @Inject AlertIngestionService ingestion;
  @Inject IncidentService incidents;
  @Inject WorkflowTables tables;
  @Inject ObjectMapper json;

  @Test
  void validTriageMovesTheIncidentToTriagedAndRecordsTheModelCall() throws Exception {
    AlertMessage alert = alert();
    when(assistant.triage(eq(alert.alertName()), any(), any(), any(), any(), any(), any()))
        .thenReturn(answer(RESULT, 812, 96));

    UUID id = ingestion.ingest(alert).incidentId();

    awaitState(id, IncidentState.TRIAGED);
    StepRow step = tables.steps(id).getFirst();
    assertThat(step.stepType()).isEqualTo("TRIAGE");
    assertThat(step.status()).isEqualTo("SUCCEEDED");
    assertThat(step.modelId()).isEqualTo("qwen2.5:3b");
    assertThat(step.promptId()).isEqualTo("triage");
    assertThat(step.promptVersion()).isEqualTo("1");
    assertThat(step.inputTokens()).isEqualTo(812);
    assertThat(step.outputTokens()).isEqualTo(96);
    assertThat(json.readValue(step.output(), TriageResult.class)).isEqualTo(RESULT);
    JsonNode input = json.readTree(step.input());
    assertThat(input.path("service").asText()).isEqualTo("checkout");
    assertThat(input.path("labels").path("env").asText()).isEqualTo("prod");
    assertThat(input.has("generatorUrl"))
        .as("only the fields of A-0014 reach the prompt")
        .isFalse();
    assertThat(tables.lease(id).owner()).as("lease released").isNull();
    verify(assistant)
        .triage(
            eq("HighErrorRate"),
            eq("prometheus"),
            eq("checkout"),
            eq("critical"),
            eq(alert.summary()),
            eq(alert.description()),
            any());
  }

  @Test
  void answerStillInvalidAfterTheRepromptEscalates() {
    AlertMessage alert = alert();
    when(assistant.triage(eq(alert.alertName()), any(), any(), any(), any(), any(), any()))
        .thenThrow(new OutputGuardrailException("Invalid triage result: summary size must be"));

    UUID id = ingestion.ingest(alert).incidentId();

    awaitState(id, IncidentState.ESCALATED);
    assertThat(tables.steps(id))
        .singleElement()
        .satisfies(
            step -> {
              assertThat(step.status()).isEqualTo("FAILED");
              assertThat(step.reasonCode()).isEqualTo("TRIAGE_INVALID_OUTPUT");
              assertThat(step.errorMessage()).contains("summary size must be");
              assertThat(step.promptVersion()).isEqualTo("1");
              assertThat(step.modelId()).isEqualTo("ollama/qwen2.5:3b");
              assertThat(step.inputTokens()).isNull();
            });
  }

  @Test
  void timeoutEscalatesAsUnavailable() {
    AlertMessage alert = alert();
    when(assistant.triage(eq(alert.alertName()), any(), any(), any(), any(), any(), any()))
        .thenThrow(new TimeoutException("timed out"));

    UUID id = ingestion.ingest(alert).incidentId();

    awaitState(id, IncidentState.ESCALATED);
    assertThat(tables.steps(id))
        .singleElement()
        .satisfies(step -> assertThat(step.reasonCode()).isEqualTo("TRIAGE_UNAVAILABLE"));
  }

  @Test
  void exceedingTheTokenBudgetEscalates() {
    AlertMessage alert = alert();
    when(assistant.triage(eq(alert.alertName()), any(), any(), any(), any(), any(), any()))
        .thenReturn(answer(RESULT, 19_000, 1_500));

    UUID id = ingestion.ingest(alert).incidentId();

    awaitState(id, IncidentState.ESCALATED);
    assertThat(tables.steps(id))
        .singleElement()
        .satisfies(step -> assertThat(step.reasonCode()).isEqualTo("TOKEN_BUDGET_EXCEEDED"));
  }

  @Test
  void atMostTwoStepsRunAtTheSameTime() throws Exception {
    String alertName = "Concurrency-" + UUID.randomUUID();
    AtomicInteger running = new AtomicInteger();
    AtomicInteger mostRunning = new AtomicInteger();
    CountDownLatch release = new CountDownLatch(1);
    when(assistant.triage(eq(alertName), any(), any(), any(), any(), any(), any()))
        .thenAnswer(
            call -> {
              mostRunning.accumulateAndGet(running.incrementAndGet(), Math::max);
              release.await(TIMEOUT.toSeconds(), TimeUnit.SECONDS);
              running.decrementAndGet();
              return answer(RESULT, 10, 10);
            });

    UUID[] ids = new UUID[4];
    for (int i = 0; i < ids.length; i++) {
      ids[i] = ingestion.ingest(named(alertName)).incidentId();
    }
    await().atMost(TIMEOUT).until(() -> running.get() == 2);
    // Several executor runs (every 100 ms) pass while both slots are busy; none starts a third.
    await().during(Duration.ofMillis(500)).atMost(TIMEOUT).until(() -> running.get() == 2);
    release.countDown();

    for (UUID id : ids) {
      awaitState(id, IncidentState.TRIAGED);
    }
    assertThat(mostRunning.get()).isEqualTo(2);
  }

  /** A new alert of its own incident, with this name. */
  private static AlertMessage named(String alertName) {
    AlertMessage alert = alert();
    return new AlertMessage(
        alert.source(),
        alert.fingerprint(),
        alertName,
        alert.summary(),
        alert.startsAt(),
        alert.severity(),
        alert.service(),
        alert.description(),
        alert.labels(),
        alert.generatorUrl());
  }

  private void awaitState(UUID id, IncidentState state) {
    await().atMost(TIMEOUT).until(() -> incidents.get(id).state() == state);
  }

  private static Result<TriageResult> answer(TriageResult result, int input, int output) {
    return Result.<TriageResult>builder()
        .content(result)
        .tokenUsage(new TokenUsage(input, output))
        .finalResponse(
            ChatResponse.builder().aiMessage(AiMessage.from("{}")).modelName("qwen2.5:3b").build())
        .build();
  }
}
