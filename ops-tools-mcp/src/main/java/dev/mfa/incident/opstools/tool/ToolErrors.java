package dev.mfa.incident.opstools.tool;

import dev.mfa.incident.opstools.exception.OpsToolException;
import io.quarkiverse.mcp.server.ToolCallException;
import java.util.function.Supplier;

/** Turns domain failures into MCP tool errors ({@code isError: true}) with their message only. */
final class ToolErrors {

  private ToolErrors() {}

  static <T> T reported(Supplier<T> call) {
    try {
      return call.get();
    } catch (OpsToolException e) {
      throw new ToolCallException(e.getMessage());
    }
  }
}
