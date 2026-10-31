package dev.mfa.incident.ai;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.invocation.InvocationContext;
import dev.langchain4j.mcp.McpToolProvider;
import dev.langchain4j.mcp.client.McpClient;
import dev.langchain4j.service.tool.AiServiceTool;
import dev.langchain4j.service.tool.ToolExecutionResult;
import dev.langchain4j.service.tool.ToolExecutor;
import dev.langchain4j.service.tool.ToolProvider;
import dev.langchain4j.service.tool.ToolProviderRequest;
import dev.langchain4j.service.tool.ToolProviderResult;
import dev.mfa.incident.dto.ToolCallDto;
import io.quarkiverse.langchain4j.mcp.runtime.McpClientName;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * The tools of {@link DiagnosisAgent} (P-0006, A-0015): the read tools of {@code ops-tools-mcp} in
 * {@link #READ_TOOLS}, and {@link KnowledgeSearchTool}. Every call is counted against the budget of
 * the current {@link DiagnosisRun} and recorded in its trace; a call beyond the budget is not
 * executed and ends the agent's call.
 *
 * <p>Only a {@code Supplier}, not a {@link ToolProvider} bean: an AI service without an explicit
 * tool provider would pick up any {@code ToolProvider} bean.
 */
@ApplicationScoped
public class DiagnosisTools implements Supplier<ToolProvider> {

  /** Name of the MCP client of {@code /mcp/read}. */
  public static final String MCP_CLIENT = "ops-read";

  /**
   * The only MCP tools the agent sees (P-0006): read tools of A-0005. A tool the server adds is not
   * offered until it is listed here.
   */
  public static final List<String> READ_TOOLS =
      List.of("get_service_metrics", "search_logs", "get_recent_deployments", "get_service_health");

  private final McpToolProvider mcpTools;
  private final KnowledgeSearchTool knowledge;

  public DiagnosisTools(
      @McpClientName(MCP_CLIENT) McpClient mcpClient, KnowledgeSearchTool knowledge) {
    this.mcpTools =
        McpToolProvider.builder()
            .mcpClients(mcpClient)
            .filterToolNames(READ_TOOLS)
            .failIfOneServerFails(true)
            .build();
    this.knowledge = knowledge;
  }

  @Override
  public ToolProvider get() {
    return this::provideTools;
  }

  ToolProviderResult provideTools(ToolProviderRequest request) {
    DiagnosisRun run = DiagnosisRun.current();
    ToolProviderResult.Builder tools = ToolProviderResult.builder();
    for (AiServiceTool tool : mcpTools.provideTools(request).aiServiceTools()) {
      if (READ_TOOLS.contains(tool.name())) {
        tools.add(
            tool.toolSpecification(),
            traced(run, tool.name(), tool.toolExecutor()::executeWithContext, result -> List.of()));
      }
    }
    tools.add(
        KnowledgeSearchTool.SPECIFICATION,
        traced(
            run,
            KnowledgeSearchTool.NAME,
            (req, context) -> knowledge.execute(req, run.service()),
            DiagnosisTools::titles));
    return tools.build();
  }

  private static ToolExecutor traced(
      DiagnosisRun run,
      String name,
      Execution delegate,
      Function<ToolExecutionResult, List<String>> titlesOf) {
    return new ToolExecutor() {
      @Override
      public String execute(ToolExecutionRequest request, Object memoryId) {
        return executeWithContext(request, null).resultText();
      }

      @Override
      public ToolExecutionResult executeWithContext(
          ToolExecutionRequest request, InvocationContext context) {
        if (!run.tryStartCall()) {
          run.record(
              new ToolCallDto(
                  name, request.arguments(), ToolCallDto.Outcome.NOT_EXECUTED, 0, 0, null));
          throw new ToolCallBudgetExceededException(run.maxToolCalls());
        }
        long started = System.nanoTime();
        ToolExecutionResult result;
        try {
          result = delegate.execute(request, context);
        } catch (RuntimeException e) {
          run.record(
              new ToolCallDto(
                  name,
                  request.arguments(),
                  ToolCallDto.Outcome.ERROR,
                  millisSince(started),
                  0,
                  null));
          throw e;
        }
        String text = result.resultText();
        run.record(
            new ToolCallDto(
                name,
                request.arguments(),
                result.isError() ? ToolCallDto.Outcome.ERROR : ToolCallDto.Outcome.OK,
                millisSince(started),
                text == null ? 0 : text.length(),
                result.isError() ? List.of() : titlesOf.apply(result)));
        return result;
      }
    };
  }

  @SuppressWarnings("unchecked")
  private static List<String> titles(ToolExecutionResult result) {
    return result.result() instanceof List<?> titles ? (List<String>) titles : List.of();
  }

  private static long millisSince(long startedNanos) {
    return (System.nanoTime() - startedNanos) / 1_000_000;
  }

  @FunctionalInterface
  private interface Execution {
    ToolExecutionResult execute(ToolExecutionRequest request, InvocationContext context);
  }
}
