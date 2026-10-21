package dev.mfa.incident.resource;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.ws.rs.core.Response;
import java.util.List;

/**
 * RFC 9457 Problem Details, the body of every error response (P-0009). {@code type} is {@code
 * about:blank} throughout, so {@code title} is the reason phrase of the status: the status code is
 * what a client acts on.
 *
 * @param violations invalid request fields; only for {@code 400} caused by validation
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
record ProblemDetail(
    String type, String title, int status, String detail, List<Violation> violations) {

  static final String MEDIA_TYPE = "application/problem+json";

  private static final String ABOUT_BLANK = "about:blank";

  static ProblemDetail of(Response.StatusType status, String detail) {
    return new ProblemDetail(
        ABOUT_BLANK, status.getReasonPhrase(), status.getStatusCode(), detail, null);
  }

  static ProblemDetail invalid(String detail, List<Violation> violations) {
    Response.Status status = Response.Status.BAD_REQUEST;
    return new ProblemDetail(
        ABOUT_BLANK, status.getReasonPhrase(), status.getStatusCode(), detail, violations);
  }

  /**
   * @param field request element, for example {@code decide.decision.comment}
   */
  record Violation(String field, String message) {}
}
