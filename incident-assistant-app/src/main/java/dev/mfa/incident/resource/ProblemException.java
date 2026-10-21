package dev.mfa.incident.resource;

import jakarta.ws.rs.core.Response;

/**
 * An error the resource layer detects in the HTTP request itself, such as a missing or malformed
 * {@code If-Match} header. Turned into a Problem Details response by {@link ProblemMappers}.
 */
final class ProblemException extends RuntimeException {

  private final Response.Status status;

  ProblemException(Response.Status status, String detail) {
    super(detail);
    this.status = status;
  }

  Response.Status status() {
    return status;
  }
}
