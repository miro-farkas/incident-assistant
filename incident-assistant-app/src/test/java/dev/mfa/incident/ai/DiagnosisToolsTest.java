package dev.mfa.incident.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.invocation.InvocationContext;
import dev.langchain4j.mcp.client.McpClient;
import dev.langchain4j.service.tool.ToolExecutionResult;
import dev.langchain4j.service.tool.ToolExecutor;
import dev.langchain4j.service.tool.ToolProviderRequest;
import dev.langchain4j.service.tool.ToolProviderResult;
import dev.mfa.incident.dto.RetrievedSegmentDto;
import dev.mfa.incident.dto.ToolCallDto;
import dev.mfa.incident.model.DocumentType;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** The allowlist, the tool call budget and the trace of the diagnosis tools (A-0015, P-0006). */
class DiagnosisToolsTest {

  private static final List<String> SERVER_TOOLS =
      List.of(
          "get_service_metrics",
          "search_logs",
          "get_recent_deployments",
          "get_service_health",
          "rollback_deployment",
          "restart_service");

  private McpClient mcp;
  private KnowledgeSearch knowledge;
  private DiagnosisTools tools;

  @BeforeEach
  void createTools() {
    mcp = mock(McpClient.class);
    List<ToolSpecification> listing =
        SERVER_TOOLS.stream()
            .map(name -> ToolSpecification.builder().name(name).description(name).build())
            .toList();
    when(mcp.listTools()).thenReturn(listing);
    when(mcp.listTools(any())).thenReturn(listing);
    knowledge = mock(KnowledgeSearch.class);
    tools = new DiagnosisTools(mcp, new KnowledgeSearchTool(knowledge, new ObjectMapper()));
  }

  @Test
  void offersTheReadToolsAndTheKnowledgeSearchOnly() {
    DiagnosisRun run = new DiagnosisRun(6, "checkout");

    ToolProviderResult result = run.run(this::provide);

    assertThat(result.tools().keySet().stream().map(ToolSpecification::name))
        .containsExactlyInAnyOrderElementsOf(
            Stream.concat(DiagnosisTools.READ_TOOLS.stream(), Stream.of(KnowledgeSearchTool.NAME))
                .toList());
  }

  @Test
  void tracesEachCallWithItsOutcome() {
    when(mcp.executeTool(any(), any()))
        .thenAnswer(
            call -> {
              ToolExecutionRequest request = call.getArgument(0);
              return request.name().equals("search_logs")
                  ? ToolExecutionResult.builder()
                      .isError(true)
                      .resultText("unknown service")
                      .build()
                  : ToolExecutionResult.builder().resultText("[]").build();
            });
    when(knowledge.search("error rate", "checkout"))
        .thenReturn(
            List.of(
                segment("Checkout runbook", "High error rate"),
                segment("Checkout runbook", "Rollback"),
                segment("Checkout bad deployment postmortem", null)));
    DiagnosisRun run = new DiagnosisRun(6, "checkout");

    String knowledgeText =
        run.run(
            () -> {
              ToolProviderResult result = provide();
              call(result, "get_recent_deployments", "{\"service\":\"checkout\"}");
              call(result, "search_logs", "{\"service\":\"nope\"}");
              return call(result, KnowledgeSearchTool.NAME, "{\"query\":\"error rate\"}");
            });

    assertThat(knowledgeText)
        .contains("<document title=\"Checkout runbook\"", "heading=\"High error rate\"");
    assertThat(run.toolCalls())
        .extracting(ToolCallDto::tool, ToolCallDto::outcome, ToolCallDto::titles)
        .containsExactly(
            tuple("get_recent_deployments", ToolCallDto.Outcome.OK, List.of()),
            tuple("search_logs", ToolCallDto.Outcome.ERROR, List.of()),
            tuple(
                KnowledgeSearchTool.NAME,
                ToolCallDto.Outcome.OK,
                List.of("Checkout runbook", "Checkout bad deployment postmortem")));
    assertThat(run.toolCalls().getFirst().arguments()).isEqualTo("{\"service\":\"checkout\"}");
    assertThat(run.toolCalls().getFirst().resultChars()).isEqualTo(2);
    assertThat(run.answeredTools())
        .containsExactlyInAnyOrder("get_recent_deployments", KnowledgeSearchTool.NAME);
    assertThat(run.documentTitles())
        .containsExactlyInAnyOrder("Checkout runbook", "Checkout bad deployment postmortem");
    assertThat(run.budgetExceeded()).isFalse();
  }

  @Test
  void callBeyondTheBudgetIsNotExecutedAndEndsTheRun() {
    when(mcp.executeTool(any(), any()))
        .thenReturn(ToolExecutionResult.builder().resultText("ok").build());
    DiagnosisRun run = new DiagnosisRun(2, "checkout");

    assertThatThrownBy(
            () ->
                run.run(
                    () -> {
                      ToolProviderResult result = provide();
                      call(result, "get_service_health", "{}");
                      call(result, "get_service_metrics", "{}");
                      return call(result, "search_logs", "{}");
                    }))
        .isInstanceOf(ToolCallBudgetExceededException.class);

    assertThat(run.budgetExceeded()).isTrue();
    assertThat(run.toolCalls())
        .extracting(ToolCallDto::outcome)
        .containsExactly(
            ToolCallDto.Outcome.OK, ToolCallDto.Outcome.OK, ToolCallDto.Outcome.NOT_EXECUTED);
    verify(mcp, never()).executeTool(eq(request("search_logs", "{}")), any());
  }

  @Test
  void knowledgeSearchUsesTheServiceOfTheRunAndRejectsBadQueries() {
    when(knowledge.search(anyString(), any())).thenReturn(List.of());
    DiagnosisRun run = new DiagnosisRun(6, null);

    run.run(
        () -> {
          ToolProviderResult result = provide();
          assertThat(call(result, KnowledgeSearchTool.NAME, "{\"query\":\" checkout latency \"}"))
              .isEqualTo("No document matches the query.");
          call(result, KnowledgeSearchTool.NAME, "{\"query\":\"" + "x".repeat(201) + "\"}");
          return call(result, KnowledgeSearchTool.NAME, "not json");
        });

    verify(knowledge).search("checkout latency", null);
    assertThat(run.toolCalls())
        .extracting(ToolCallDto::outcome)
        .containsExactly(
            ToolCallDto.Outcome.OK, ToolCallDto.Outcome.ERROR, ToolCallDto.Outcome.ERROR);
  }

  @Test
  void failingToolIsRecordedAndPropagates() {
    when(mcp.executeTool(any(), any())).thenThrow(new IllegalStateException("connection reset"));
    DiagnosisRun run = new DiagnosisRun(6, "checkout");

    assertThatThrownBy(
            () ->
                run.run(() -> call(provide(), "get_service_health", "{\"service\":\"checkout\"}")))
        .hasMessageContaining("connection reset");

    assertThat(run.toolCalls())
        .extracting(ToolCallDto::outcome)
        .containsExactly(ToolCallDto.Outcome.ERROR);
    assertThat(run.answeredTools()).isEmpty();
  }

  @Test
  void failingToolListingFailsTheCall() {
    when(mcp.listTools()).thenThrow(new IllegalStateException("server down"));
    when(mcp.listTools(any())).thenThrow(new IllegalStateException("server down"));
    DiagnosisRun run = new DiagnosisRun(6, "checkout");

    assertThatThrownBy(() -> run.run(this::provide)).isInstanceOf(RuntimeException.class);
  }

  @Test
  void toolsAreOnlyAvailableWithinADiagnosisRun() {
    assertThatThrownBy(this::provide).isInstanceOf(IllegalStateException.class);
  }

  private ToolProviderResult provide() {
    return tools
        .get()
        .provideTools(
            ToolProviderRequest.builder()
                .invocationContext(InvocationContext.builder().build())
                .userMessage(UserMessage.from("Diagnose"))
                .build());
  }

  private static String call(ToolProviderResult result, String tool, String arguments) {
    ToolExecutor executor =
        result.tools().entrySet().stream()
            .filter(entry -> entry.getKey().name().equals(tool))
            .map(Map.Entry::getValue)
            .findFirst()
            .orElseThrow();
    return executor
        .executeWithContext(request(tool, arguments), InvocationContext.builder().build())
        .resultText();
  }

  private static ToolExecutionRequest request(String tool, String arguments) {
    return ToolExecutionRequest.builder().id(tool).name(tool).arguments(arguments).build();
  }

  private static RetrievedSegmentDto segment(String title, String heading) {
    return new RetrievedSegmentDto(
        title,
        "runbooks/checkout.md",
        "checkout",
        DocumentType.RUNBOOK,
        heading,
        "Check the release.",
        0.9);
  }
}
