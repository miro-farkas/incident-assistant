package dev.mfa.incident.opstools.exception;

/**
 * A tool call that cannot be answered. The message is returned to the MCP client as the tool error,
 * so it explains the problem to the caller and contains no internal details.
 */
public abstract class OpsToolException extends RuntimeException {

  protected OpsToolException(String message) {
    super(message);
  }
}
