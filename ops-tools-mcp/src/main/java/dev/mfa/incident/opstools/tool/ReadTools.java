package dev.mfa.incident.opstools.tool;

import static dev.mfa.incident.opstools.tool.ToolErrors.reported;

import dev.mfa.incident.opstools.dto.DeploymentHistory;
import dev.mfa.incident.opstools.dto.LogLevel;
import dev.mfa.incident.opstools.dto.LogSearchResult;
import dev.mfa.incident.opstools.dto.ServiceHealth;
import dev.mfa.incident.opstools.dto.ServiceMetrics;
import dev.mfa.incident.opstools.service.OpsReadService;
import io.quarkiverse.mcp.server.McpServer;
import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.Tool.Annotations;
import io.quarkiverse.mcp.server.ToolArg;
import io.smallrye.common.annotation.RunOnVirtualThread;
import jakarta.inject.Singleton;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Read tools: no side effects, bounded structured results (P-0006). Served only by the {@value
 * ToolGroups#READ_SERVER} MCP server, the one the diagnosis agent may use.
 */
@Singleton
@McpServer(ToolGroups.READ_SERVER)
public class ReadTools {

  static final int MAX_SERVICE_LENGTH = 100;
  static final int MAX_QUERY_LENGTH = 200;

  private final OpsReadService reads;

  public ReadTools(OpsReadService reads) {
    this.reads = reads;
  }

  @Tool(
      name = ToolGroups.GET_SERVICE_METRICS,
      description =
          """
          Returns CPU %, heap memory %, requests per second, error rate % and p99 latency (ms) \
          of a service, one point per step over a recent time window, oldest point first. Use it \
          to see when a problem started and which signal changed. At most 120 points are \
          returned; for a longer window the newest points are kept and 'truncated' is true.\
          """,
      structuredContent = true,
      annotations =
          @Annotations(readOnlyHint = true, destructiveHint = false, openWorldHint = false))
  @RunOnVirtualThread
  public ServiceMetrics getServiceMetrics(
      @ToolArg(description = "Service name, for example checkout")
          @NotBlank
          @Size(max = MAX_SERVICE_LENGTH)
          String service,
      @ToolArg(
              description = "Length of the window ending now, in minutes (5-1440)",
              required = false,
              defaultValue = "60")
          @Min(5)
          @Max(1440)
          int windowMinutes,
      @ToolArg(
              description = "Minutes between two points (1-60)",
              required = false,
              defaultValue = "5")
          @Min(1)
          @Max(60)
          int stepMinutes) {
    return reported(() -> reads.metrics(service, windowMinutes, stepMinutes));
  }

  @Tool(
      name = ToolGroups.SEARCH_LOGS,
      description =
          """
          Searches the recent logs of a service and returns matching entries, newest first, with \
          timestamp, level, pod and message. Use it to find errors and their messages. Filter by \
          minimum level and by text the message must contain (case-insensitive). Returns at most \
          'limit' entries (max 100); 'totalMatches' counts all matches and 'truncated' is true \
          when more matched. Messages longer than 500 characters are cut and marked with \
          'messageTruncated'.\
          """,
      structuredContent = true,
      annotations =
          @Annotations(readOnlyHint = true, destructiveHint = false, openWorldHint = false))
  @RunOnVirtualThread
  public LogSearchResult searchLogs(
      @ToolArg(description = "Service name, for example checkout")
          @NotBlank
          @Size(max = MAX_SERVICE_LENGTH)
          String service,
      @ToolArg(
              description = "Text the message must contain; empty matches every message",
              required = false,
              defaultValue = "")
          @Size(max = MAX_QUERY_LENGTH)
          String query,
      @ToolArg(
              description = "Lowest level returned: DEBUG, INFO, WARN or ERROR",
              required = false,
              defaultValue = "INFO")
          LogLevel minLevel,
      @ToolArg(
              description = "How far back to search, in minutes (1-1440)",
              required = false,
              defaultValue = "60")
          @Min(1)
          @Max(1440)
          int sinceMinutes,
      @ToolArg(description = "Most entries returned (1-100)", required = false, defaultValue = "20")
          @Min(1)
          @Max(100)
          int limit) {
    return reported(() -> reads.searchLogs(service, query, minLevel, sinceMinutes, limit));
  }

  @Tool(
      name = ToolGroups.GET_RECENT_DEPLOYMENTS,
      description =
          """
          Returns the most recent deployments of a service, newest first, with version, time, \
          author and a short change description. The first entry is the running version. Use it \
          to check whether a problem started right after a deployment. Returns at most 'limit' \
          entries (max 20); 'truncated' is true when older deployments exist.\
          """,
      structuredContent = true,
      annotations =
          @Annotations(readOnlyHint = true, destructiveHint = false, openWorldHint = false))
  @RunOnVirtualThread
  public DeploymentHistory getRecentDeployments(
      @ToolArg(description = "Service name, for example checkout")
          @NotBlank
          @Size(max = MAX_SERVICE_LENGTH)
          String service,
      @ToolArg(
              description = "Most deployments returned (1-20)",
              required = false,
              defaultValue = "5")
          @Min(1)
          @Max(20)
          int limit) {
    return reported(() -> reads.recentDeployments(service, limit));
  }

  @Tool(
      name = ToolGroups.GET_SERVICE_HEALTH,
      description =
          """
          Returns the current health of a service: overall status (UP, DEGRADED, DOWN), running \
          version, desired and ready replicas, pod restarts in the last hour, and the status and \
          p99 latency of each downstream dependency. Use it first to get an overview, and to see \
          whether a dependency is failing.\
          """,
      structuredContent = true,
      annotations =
          @Annotations(readOnlyHint = true, destructiveHint = false, openWorldHint = false))
  @RunOnVirtualThread
  public ServiceHealth getServiceHealth(
      @ToolArg(description = "Service name, for example checkout")
          @NotBlank
          @Size(max = MAX_SERVICE_LENGTH)
          String service) {
    return reported(() -> reads.health(service));
  }
}
