package dev.mfa.incident.exception;

/** Base type for violations of incident workflow rules. */
public abstract class IncidentException extends RuntimeException {

  protected IncidentException(String message) {
    super(message);
  }
}
