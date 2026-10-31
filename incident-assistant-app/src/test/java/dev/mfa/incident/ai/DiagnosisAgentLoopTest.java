package dev.mfa.incident.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.guardrail.OutputGuardrailException;
import dev.langchain4j.mcp.client.McpClient;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.output.TokenUsage;
import dev.langchain4j.service.Result;
import dev.langchain4j.service.tool.ToolExecutionResult;
import dev.mfa.incident.dto.Diagnosis;
import dev.mfa.incident.dto.ToolCallDto;
import io.quarkiverse.langchain4j.ModelName;
import io.quarkiverse.langchain4j.mcp.runtime.McpClientName;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import jakarta.enterprise.inject.Alternative;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

/**
 * The real {@link DiagnosisAgent} loop (A-0015) against a scripted model and a fake {@code
 * ops-tools-mcp}: what the model is offered, the budget, the trace and the evidence check. No real
 * model or server is called (CLAUDE.md).
 */
@QuarkusTest
@TestProfile(DiagnosisAgentLoopTest.ScriptedProfile.class)
class DiagnosisAgentLoopTest {

  private static final String VALID_ANSWER =
      """
      {"hypothesis": "Release 2026.10.18-1 of checkout broke the payment call.",
       "confidence": "HIGH",
       "evidence": [{"statement": "Errors started with the deployment.",
                     "sourceType": "TOOL", "source": "get_recent_deployments"}]}\
      """;

  private static final String UNCONSULTED_ANSWER =
      VALID_ANSWER.replace("get_recent_deployments", "get_pod_events");

  @Inject DiagnosisAgent agent;

  @Inject
  @ModelName("diagnosis")
  ScriptedChatModel model;

  @Inject FakeOpsTools opsTools;
  @InjectMock KnowledgeSearch knowledge;

  @BeforeEach
  void resetFakes() {
    model.reset();
    opsTools.reset();
  }

  @Test
  void offersOnlyTheAllowedToolsAndTracesTheirCalls() {
    model.script(
        toolCall("get_recent_deployments", "{\"service\":\"checkout\"}"), text(VALID_ANSWER));
    DiagnosisRun run = new DiagnosisRun(6, "checkout");

    Result<Diagnosis> result = run.run(this::diagnose);

    assertThat(result.content().confidence()).isEqualTo(Diagnosis.Confidence.HIGH);
    assertThat(model.requests()).hasSize(2);
    assertThat(
            model.requests().getFirst().toolSpecifications().stream().map(ToolSpecification::name))
        .containsExactlyInAnyOrderElementsOf(
            Stream.concat(DiagnosisTools.READ_TOOLS.stream(), Stream.of(KnowledgeSearchTool.NAME))
                .toList());
    List<ChatMessage> first = model.requests().getFirst().messages();
    assertThat(first).hasSize(2);
    assertThat(((SystemMessage) first.getFirst()).text()).contains("at most 6 tool calls");
    assertThat(((UserMessage) first.getLast()).singleText())
        .contains("HighErrorRate", "<triage>", "DEPLOYMENT");
    assertThat(model.requests().getLast().messages())
        .filteredOn(ToolExecutionResultMessage.class::isInstance)
        .singleElement()
        .satisfies(
            message ->
                assertThat(((ToolExecutionResultMessage) message).text()).contains("2026.10.18-1"));
    assertThat(run.toolCalls())
        .singleElement()
        .satisfies(
            call -> {
              assertThat(call.tool()).isEqualTo("get_recent_deployments");
              assertThat(call.outcome()).isEqualTo(ToolCallDto.Outcome.OK);
            });
  }

  @Test
  void callBeyondTheBudgetEndsTheDiagnosis() {
    model.script(
        toolCall("get_service_health", "{}"),
        toolCall("get_service_metrics", "{}"),
        toolCall("search_logs", "{}"),
        text(VALID_ANSWER));
    DiagnosisRun run = new DiagnosisRun(2, "checkout");

    assertThatThrownBy(() -> run.run(this::diagnose))
        .isInstanceOf(ToolCallBudgetExceededException.class);

    assertThat(run.budgetExceeded()).isTrue();
    assertThat(model.requests()).as("no answer is asked for after the overrun").hasSize(3);
    assertThat(run.toolCalls())
        .extracting(ToolCallDto::outcome)
        .containsExactly(
            ToolCallDto.Outcome.OK, ToolCallDto.Outcome.OK, ToolCallDto.Outcome.NOT_EXECUTED);
  }

  @Test
  void evidenceFromAnUnconsultedSourceIsRepromptedOnce() {
    model.script(
        toolCall("get_recent_deployments", "{\"service\":\"checkout\"}"),
        text(UNCONSULTED_ANSWER),
        text(VALID_ANSWER));
    DiagnosisRun run = new DiagnosisRun(6, "checkout");

    Result<Diagnosis> result = run.run(this::diagnose);

    assertThat(result.content().evidence().getFirst().source()).isEqualTo("get_recent_deployments");
    assertThat(model.requests()).hasSize(3);
    assertThat(model.requests().getLast().messages().getLast())
        .isInstanceOfSatisfying(
            UserMessage.class,
            message -> assertThat(message.singleText()).contains("Cite only documents and tools"));
  }

  @Test
  void evidenceStillUnconsultedAfterTheRepromptFails() {
    model.script(text(UNCONSULTED_ANSWER), text(UNCONSULTED_ANSWER));
    DiagnosisRun run = new DiagnosisRun(6, "checkout");

    assertThatThrownBy(() -> run.run(this::diagnose))
        .isInstanceOf(OutputGuardrailException.class)
        .hasMessageContaining("TOOL 'get_pod_events'");
  }

  private Result<Diagnosis> diagnose() {
    return agent.diagnose(
        6,
        "HighErrorRate",
        "prometheus",
        "checkout",
        "critical",
        "Error rate above 5%",
        "5xx responses of the checkout API",
        "{\"env\":\"prod\"}",
        "HIGH",
        "DEPLOYMENT",
        "checkout",
        "Checkout fails since the last release.");
  }

  private static AiMessage toolCall(String tool, String arguments) {
    return AiMessage.from(
        ToolExecutionRequest.builder().id(tool + "-call").name(tool).arguments(arguments).build());
  }

  private static AiMessage text(String text) {
    return AiMessage.from(text);
  }

  /** Enables the scripted model and the fake MCP client for this test only. */
  public static class ScriptedProfile implements QuarkusTestProfile {

    @Override
    public Set<Class<?>> getEnabledAlternatives() {
      return Set.of(ScriptedChatModel.class, FakeOpsTools.class);
    }
  }

  /** The {@code diagnosis} model: answers with the scripted messages in order. */
  @Alternative
  @Singleton
  @ModelName("diagnosis")
  public static class ScriptedChatModel implements ChatModel {

    private final Deque<AiMessage> answers = new ArrayDeque<>();
    private final List<ChatRequest> requests = new ArrayList<>();

    synchronized void script(AiMessage... messages) {
      answers.addAll(List.of(messages));
    }

    synchronized List<ChatRequest> requests() {
      return List.copyOf(requests);
    }

    synchronized void reset() {
      answers.clear();
      requests.clear();
    }

    @Override
    public synchronized ChatResponse doChat(ChatRequest request) {
      requests.add(request);
      AiMessage answer = answers.poll();
      if (answer == null) {
        throw new IllegalStateException("No scripted answer left");
      }
      return ChatResponse.builder()
          .aiMessage(answer)
          .tokenUsage(new TokenUsage(100, 20))
          .modelName("scripted")
          .build();
    }
  }

  /** The {@code ops-read} MCP client: lists read and action tools, answers every call. */
  @Alternative
  @Singleton
  public static class FakeOpsTools {

    private static final List<String> SERVER_TOOLS =
        List.of(
            "get_service_metrics",
            "search_logs",
            "get_recent_deployments",
            "get_service_health",
            "rollback_deployment");

    private final McpClient client = mock(McpClient.class);

    void reset() {
      Mockito.reset(client);
      List<ToolSpecification> listing =
          SERVER_TOOLS.stream()
              .map(name -> ToolSpecification.builder().name(name).description(name).build())
              .toList();
      when(client.listTools()).thenReturn(listing);
      when(client.listTools(any())).thenReturn(listing);
      when(client.executeTool(any(), any()))
          .thenReturn(
              ToolExecutionResult.builder()
                  .resultText("[{\"service\":\"checkout\",\"version\":\"2026.10.18-1\"}]")
                  .build());
    }

    @Produces
    @Singleton
    @McpClientName(DiagnosisTools.MCP_CLIENT)
    McpClient client() {
      return client;
    }
  }
}
