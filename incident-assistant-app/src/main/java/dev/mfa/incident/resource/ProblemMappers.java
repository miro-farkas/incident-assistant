package dev.mfa.incident.resource;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.exc.MismatchedInputException;
import dev.mfa.incident.exception.DecisionNotAllowedException;
import dev.mfa.incident.exception.IncidentNotFoundException;
import dev.mfa.incident.exception.IncidentVersionConflictException;
import dev.mfa.incident.exception.IngestionInProgressException;
import dev.mfa.incident.exception.StreamLimitExceededException;
import dev.mfa.incident.exception.UnknownIncidentStateException;
import io.quarkus.security.AuthenticationFailedException;
import io.quarkus.security.ForbiddenException;
import io.quarkus.security.UnauthorizedException;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.ElementKind;
import jakarta.validation.Path;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.Response;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
import org.jboss.logging.Logger;
import org.jboss.resteasy.reactive.server.ServerExceptionMapper;

/**
 * Turns every error of the REST API into RFC 9457 Problem Details (P-0009). Details never echo
 * request content; validation errors name the invalid fields.
 */
public class ProblemMappers {

  private static final Logger LOG = Logger.getLogger(ProblemMappers.class);

  private static final String BEARER_CHALLENGE = "Bearer";

  @ServerExceptionMapper
  public Response notFound(IncidentNotFoundException e) {
    return problem(Response.Status.NOT_FOUND, "Incident " + e.incidentId() + " not found");
  }

  @ServerExceptionMapper
  public Response versionConflict(IncidentVersionConflictException e) {
    return problem(
        Response.Status.PRECONDITION_FAILED,
        "Incident "
            + e.incidentId()
            + " is no longer at version "
            + e.expectedVersion()
            + "; reload it and decide again");
  }

  @ServerExceptionMapper
  public Response decisionNotAllowed(DecisionNotAllowedException e) {
    return problem(Response.Status.CONFLICT, e.getMessage());
  }

  @ServerExceptionMapper
  public Response ingestionInProgress(IngestionInProgressException e) {
    return problem(Response.Status.CONFLICT, e.getMessage());
  }

  @ServerExceptionMapper
  public Response streamLimit(StreamLimitExceededException e) {
    return Response.fromResponse(problem(Response.Status.SERVICE_UNAVAILABLE, e.getMessage()))
        .header(HttpHeaders.RETRY_AFTER, e.retryAfter().toSeconds())
        .build();
  }

  @ServerExceptionMapper
  public Response unknownState(UnknownIncidentStateException e) {
    return problem(Response.Status.BAD_REQUEST, e.getMessage());
  }

  @ServerExceptionMapper
  public Response requestProblem(ProblemException e) {
    return problem(e.status(), e.getMessage());
  }

  @ServerExceptionMapper
  public Response invalid(ConstraintViolationException e) {
    if (e.getConstraintViolations().stream().anyMatch(ProblemMappers::isReturnValue)) {
      LOG.errorf(e, "Response failed validation");
      return problem(Response.Status.INTERNAL_SERVER_ERROR, "Internal error");
    }
    List<ProblemDetail.Violation> violations =
        e.getConstraintViolations().stream()
            .map(v -> new ProblemDetail.Violation(v.getPropertyPath().toString(), v.getMessage()))
            .sorted(Comparator.comparing(ProblemDetail.Violation::field))
            .toList();
    return Response.status(Response.Status.BAD_REQUEST)
        .type(ProblemDetail.MEDIA_TYPE)
        .entity(ProblemDetail.invalid("The request is invalid", violations))
        .build();
  }

  /**
   * Registered for {@link MismatchedInputException} as well, because Quarkus REST has a built-in
   * mapper for that type that would otherwise win as the more specific one.
   */
  @ServerExceptionMapper({JsonProcessingException.class, MismatchedInputException.class})
  public Response unreadableBody(JsonProcessingException e) {
    String detail = "The request body is not valid JSON for this operation";
    if (e instanceof JsonMappingException mapping && !mapping.getPath().isEmpty()) {
      detail +=
          " (at "
              + mapping.getPath().stream()
                  .map(r -> r.getFieldName() != null ? r.getFieldName() : "[" + r.getIndex() + "]")
                  .collect(Collectors.joining("."))
              + ")";
    }
    return problem(Response.Status.BAD_REQUEST, detail);
  }

  @ServerExceptionMapper({UnauthorizedException.class, AuthenticationFailedException.class})
  public Response unauthenticated(RuntimeException e) {
    return Response.fromResponse(
            problem(Response.Status.UNAUTHORIZED, "A valid bearer token is required"))
        .header(HttpHeaders.WWW_AUTHENTICATE, BEARER_CHALLENGE)
        .build();
  }

  @ServerExceptionMapper
  public Response forbidden(ForbiddenException e) {
    return problem(Response.Status.FORBIDDEN, "The token lacks the role this operation requires");
  }

  @ServerExceptionMapper
  public Response webApplication(WebApplicationException e) {
    Response original = e.getResponse();
    Response.StatusType status = original.getStatusInfo();
    return Response.fromResponse(original)
        .type(ProblemDetail.MEDIA_TYPE)
        .entity(ProblemDetail.of(status, status.getReasonPhrase()))
        .build();
  }

  @ServerExceptionMapper
  public Response unexpected(RuntimeException e) {
    LOG.errorf(e, "Unhandled error in the REST API");
    return problem(Response.Status.INTERNAL_SERVER_ERROR, "Internal error");
  }

  private static Response problem(Response.StatusType status, String detail) {
    return Response.status(status)
        .type(ProblemDetail.MEDIA_TYPE)
        .entity(ProblemDetail.of(status, detail))
        .build();
  }

  private static boolean isReturnValue(ConstraintViolation<?> violation) {
    Iterable<Path.Node> nodes = violation.getPropertyPath();
    return StreamSupport.stream(nodes.spliterator(), false)
        .anyMatch(node -> node.getKind() == ElementKind.RETURN_VALUE);
  }
}
