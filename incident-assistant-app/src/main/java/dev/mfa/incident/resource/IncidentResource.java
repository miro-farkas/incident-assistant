package dev.mfa.incident.resource;

import dev.mfa.incident.config.IncidentStreamConfig;
import dev.mfa.incident.dto.DecisionDto;
import dev.mfa.incident.dto.IncidentDetailDto;
import dev.mfa.incident.dto.IncidentPageDto;
import dev.mfa.incident.dto.StreamEventDto;
import dev.mfa.incident.service.IncidentQueryService;
import dev.mfa.incident.service.IncidentService;
import dev.mfa.incident.service.IncidentStreamService;
import io.quarkus.security.identity.SecurityIdentity;
import io.smallrye.mutiny.Multi;
import jakarta.annotation.security.RolesAllowed;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.sse.OutboundSseEvent;
import jakarta.ws.rs.sse.Sse;
import java.util.List;
import java.util.UUID;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.enums.ParameterIn;
import org.eclipse.microprofile.openapi.annotations.headers.Header;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.jboss.resteasy.reactive.RestResponse;

/** Approval API (A-0006): maps HTTP to {@link IncidentQueryService} and {@link IncidentService}. */
@Path("/v1/incidents")
@Produces(MediaType.APPLICATION_JSON)
@Tag(name = "Incidents")
@APIResponse(
    responseCode = "401",
    description = "No or invalid bearer token",
    content = @Content(mediaType = ProblemDetail.MEDIA_TYPE))
@APIResponse(
    responseCode = "403",
    description = "Role missing",
    content = @Content(mediaType = ProblemDetail.MEDIA_TYPE))
public class IncidentResource {

  private static final String DEFAULT_PAGE_SIZE = "20";
  private static final String HEARTBEAT_COMMENT = "heartbeat";

  private final IncidentQueryService queries;
  private final IncidentService incidents;
  private final IncidentStreamService stream;
  private final IncidentStreamConfig streamConfig;
  private final SecurityIdentity identity;

  public IncidentResource(
      IncidentQueryService queries,
      IncidentService incidents,
      IncidentStreamService stream,
      IncidentStreamConfig streamConfig,
      SecurityIdentity identity) {
    this.queries = queries;
    this.incidents = incidents;
    this.stream = stream;
    this.streamConfig = streamConfig;
    this.identity = identity;
  }

  @GET
  @RolesAllowed({Roles.VIEWER, Roles.APPROVER})
  @Operation(summary = "List incidents, newest first")
  @APIResponse(responseCode = "200", description = "One page of incidents")
  @APIResponse(
      responseCode = "400",
      description = "Unknown state or invalid paging",
      content = @Content(mediaType = ProblemDetail.MEDIA_TYPE))
  public IncidentPageDto list(
      @Parameter(description = "Repeat to include several states; all states when absent")
          @QueryParam("state")
          List<String> states,
      @QueryParam("page") @DefaultValue("0") @Min(0) int page,
      @QueryParam("size")
          @DefaultValue(DEFAULT_PAGE_SIZE)
          @Min(1)
          @Max(IncidentQueryService.MAX_PAGE_SIZE)
          int size) {
    return queries.list(states, page, size);
  }

  @GET
  @Path("/stream")
  @Produces(MediaType.SERVER_SENT_EVENTS)
  @RolesAllowed({Roles.VIEWER, Roles.APPROVER})
  @Operation(
      summary = "Live incident events (Server-Sent Events)",
      description =
          "Every incident.state-changed and incident.occurrence-recorded event from now on,"
              + " unchanged (event = eventType, id = eventId), plus heartbeat comments. Events"
              + " are hints: reload what you show on (re)connect; there is no replay.")
  @APIResponse(responseCode = "200", description = "Event stream")
  @APIResponse(
      responseCode = "503",
      description = "Stream limit of this instance reached; see Retry-After",
      headers = @Header(name = HttpHeaders.RETRY_AFTER, description = "Seconds to wait"),
      content = @Content(mediaType = ProblemDetail.MEDIA_TYPE))
  public Multi<OutboundSseEvent> stream(@Context Sse sse) {
    Multi<OutboundSseEvent> events = stream.open().map(event -> toSseEvent(sse, event));
    Multi<OutboundSseEvent> heartbeats =
        Multi.createFrom()
            .ticks()
            .every(streamConfig.heartbeatInterval())
            .onOverflow()
            .drop()
            .map(tick -> sse.newEventBuilder().comment(HEARTBEAT_COMMENT).build());
    return Multi.createBy().merging().streams(events, heartbeats);
  }

  @GET
  @Path("/{id}")
  @RolesAllowed({Roles.VIEWER, Roles.APPROVER})
  @Operation(summary = "Incident with alert, step history and decision")
  @APIResponse(
      responseCode = "200",
      description = "The incident; ETag is its version",
      headers = @Header(name = HttpHeaders.ETAG, description = "Incident version, e.g. \"3\""))
  @APIResponse(
      responseCode = "404",
      description = "Unknown incident",
      content = @Content(mediaType = ProblemDetail.MEDIA_TYPE))
  public RestResponse<IncidentDetailDto> get(@PathParam("id") UUID id) {
    return withEntityTag(queries.detail(id));
  }

  @POST
  @Path("/{id}/decision")
  @Consumes(MediaType.APPLICATION_JSON)
  @RolesAllowed(Roles.APPROVER)
  @Operation(summary = "Approve or reject the pending remediation proposal")
  @APIResponse(
      responseCode = "200",
      description = "Decision recorded; the incident after it",
      headers = @Header(name = HttpHeaders.ETAG, description = "New incident version"))
  @APIResponse(
      responseCode = "400",
      description = "Invalid body or If-Match",
      content = @Content(mediaType = ProblemDetail.MEDIA_TYPE))
  @APIResponse(
      responseCode = "404",
      description = "Unknown incident",
      content = @Content(mediaType = ProblemDetail.MEDIA_TYPE))
  @APIResponse(
      responseCode = "409",
      description = "Incident is not awaiting approval",
      content = @Content(mediaType = ProblemDetail.MEDIA_TYPE))
  @APIResponse(
      responseCode = "412",
      description = "Incident changed since the version in If-Match",
      content = @Content(mediaType = ProblemDetail.MEDIA_TYPE))
  @APIResponse(
      responseCode = "428",
      description = "If-Match missing",
      content = @Content(mediaType = ProblemDetail.MEDIA_TYPE))
  public RestResponse<IncidentDetailDto> decide(
      @PathParam("id") UUID id,
      @Parameter(
              in = ParameterIn.HEADER,
              required = true,
              description = "ETag of the incident version the decision is based on",
              schema = @Schema(example = "\"3\""))
          @HeaderParam(HttpHeaders.IF_MATCH)
          String ifMatch,
      @Valid @NotNull DecisionDto decision) {
    long expectedVersion = IncidentVersions.fromIfMatch(ifMatch);
    String approver = identity.getPrincipal().getName();
    return withEntityTag(incidents.decide(id, expectedVersion, decision, approver));
  }

  /**
   * The payload is already JSON text and is written as is. No media type is set on the event:
   * Quarkus would add a non-standard {@code content-type} field to every message.
   */
  private static OutboundSseEvent toSseEvent(Sse sse, StreamEventDto event) {
    return sse.newEventBuilder()
        .name(event.eventType())
        .id(event.eventId().toString())
        .data(event.payload())
        .build();
  }

  private static RestResponse<IncidentDetailDto> withEntityTag(IncidentDetailDto incident) {
    return RestResponse.ResponseBuilder.ok(incident)
        .header(HttpHeaders.ETAG, IncidentVersions.entityTag(incident.version()))
        .build();
  }
}
