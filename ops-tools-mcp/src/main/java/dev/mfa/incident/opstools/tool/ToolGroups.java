package dev.mfa.incident.opstools.tool;

import java.util.Set;

/**
 * The single declaration of which tool belongs to which group (P-0006, A-0005). Read tools are
 * served by MCP server {@value #READ_SERVER} at {@code /mcp/read}, action tools by MCP server
 * {@value #ACTIONS_SERVER} at {@code /mcp/actions}, which requires role {@code ops-actions}.
 */
public final class ToolGroups {

  public static final String READ_SERVER = "read";
  public static final String ACTIONS_SERVER = "actions";

  public static final String GET_SERVICE_METRICS = "get_service_metrics";
  public static final String SEARCH_LOGS = "search_logs";
  public static final String GET_RECENT_DEPLOYMENTS = "get_recent_deployments";
  public static final String GET_SERVICE_HEALTH = "get_service_health";

  public static final String RESTART_SERVICE = "restart_service";
  public static final String ROLLBACK_DEPLOYMENT = "rollback_deployment";
  public static final String SCALE_SERVICE = "scale_service";

  public static final Set<String> READ_TOOLS =
      Set.of(GET_SERVICE_METRICS, SEARCH_LOGS, GET_RECENT_DEPLOYMENTS, GET_SERVICE_HEALTH);

  public static final Set<String> ACTION_TOOLS =
      Set.of(RESTART_SERVICE, ROLLBACK_DEPLOYMENT, SCALE_SERVICE);

  private ToolGroups() {}
}
