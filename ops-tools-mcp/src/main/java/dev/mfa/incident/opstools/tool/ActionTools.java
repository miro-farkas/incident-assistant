package dev.mfa.incident.opstools.tool;

import static dev.mfa.incident.opstools.tool.ToolErrors.reported;

import dev.mfa.incident.opstools.dto.ActionResult;
import dev.mfa.incident.opstools.security.ApiKeyAuthenticationMechanism;
import dev.mfa.incident.opstools.service.OpsActionService;
import io.quarkiverse.mcp.server.McpServer;
import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.Tool.Annotations;
import io.quarkiverse.mcp.server.ToolArg;
import io.smallrye.common.annotation.RunOnVirtualThread;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Singleton;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Action tools: change the state of a service, only after human approval (P-0006). Served only by
 * the {@value ToolGroups#ACTIONS_SERVER} MCP server, whose HTTP path requires role {@value
 * ApiKeyAuthenticationMechanism#ACTIONS_ROLE}; the role is checked again on every call. Every call
 * needs an idempotency key: a repeated key returns the original result without executing again.
 */
@Singleton
@McpServer(ToolGroups.ACTIONS_SERVER)
@RolesAllowed(ApiKeyAuthenticationMechanism.ACTIONS_ROLE)
public class ActionTools {

  /** Long enough for two UUIDs joined by a separator (incident id and approval id, A-0005). */
  static final String IDEMPOTENCY_KEY_PATTERN = "[A-Za-z0-9][A-Za-z0-9._:-]{7,127}";

  static final String VERSION_PATTERN = "[0-9A-Za-z][0-9A-Za-z.+-]{0,31}";

  private static final String SERVICE_DESCRIPTION = "Service name, for example checkout";
  private static final String KEY_DESCRIPTION =
      "Unique key of this action request, 8-128 characters of letters, digits and . _ : -;"
          + " a repeated key returns the original result";

  private final OpsActionService actions;

  public ActionTools(OpsActionService actions) {
    this.actions = actions;
  }

  @Tool(
      name = ToolGroups.RESTART_SERVICE,
      description =
          """
          Restarts all pods of a service one at a time. Returns the action result with status \
          COMPLETED.\
          """,
      structuredContent = true,
      annotations =
          @Annotations(destructiveHint = true, idempotentHint = true, openWorldHint = false))
  @RunOnVirtualThread
  public ActionResult restartService(
      @ToolArg(description = SERVICE_DESCRIPTION)
          @NotBlank
          @Size(max = ReadTools.MAX_SERVICE_LENGTH)
          String service,
      @ToolArg(description = KEY_DESCRIPTION) @NotNull @Pattern(regexp = IDEMPOTENCY_KEY_PATTERN)
          String idempotencyKey) {
    return reported(() -> actions.restart(service, idempotencyKey));
  }

  @Tool(
      name = ToolGroups.ROLLBACK_DEPLOYMENT,
      description =
          """
          Rolls a service back to an earlier release: the given target version, or the release \
          before the running one. Returns status COMPLETED, or REJECTED when the target is not an \
          earlier release of the service.\
          """,
      structuredContent = true,
      annotations =
          @Annotations(destructiveHint = true, idempotentHint = true, openWorldHint = false))
  @RunOnVirtualThread
  public ActionResult rollbackDeployment(
      @ToolArg(description = SERVICE_DESCRIPTION)
          @NotBlank
          @Size(max = ReadTools.MAX_SERVICE_LENGTH)
          String service,
      @ToolArg(
              description = "Version to roll back to; empty for the release before the running one",
              required = false,
              defaultValue = "")
          @Pattern(regexp = "(" + VERSION_PATTERN + ")?")
          String targetVersion,
      @ToolArg(description = KEY_DESCRIPTION) @NotNull @Pattern(regexp = IDEMPOTENCY_KEY_PATTERN)
          String idempotencyKey) {
    String target = targetVersion == null || targetVersion.isEmpty() ? null : targetVersion;
    return reported(() -> actions.rollback(service, target, idempotencyKey));
  }

  @Tool(
      name = ToolGroups.SCALE_SERVICE,
      description =
          """
          Sets the number of replicas of a service. Returns status COMPLETED, or REJECTED when the \
          number exceeds the maximum of the service.\
          """,
      structuredContent = true,
      annotations =
          @Annotations(destructiveHint = true, idempotentHint = true, openWorldHint = false))
  @RunOnVirtualThread
  public ActionResult scaleService(
      @ToolArg(description = SERVICE_DESCRIPTION)
          @NotBlank
          @Size(max = ReadTools.MAX_SERVICE_LENGTH)
          String service,
      @ToolArg(description = "Desired number of replicas (1-50)") @Min(1) @Max(50) int replicas,
      @ToolArg(description = KEY_DESCRIPTION) @NotNull @Pattern(regexp = IDEMPOTENCY_KEY_PATTERN)
          String idempotencyKey) {
    return reported(() -> actions.scale(service, replicas, idempotencyKey));
  }
}
