package dev.mfa.incident.exception;

/**
 * An alert message that cannot be ingested: too large, not JSON, or failing validation. The message
 * names the rule and the field, never the received values, because it ends up in the dead-letter
 * header and in logs (A-0003). It has no cause for the same reason.
 */
public final class InvalidAlertException extends RuntimeException {

  public InvalidAlertException(String message) {
    super(message);
  }
}
