package dev.mfa.incident.resource;

import dev.mfa.incident.dto.CurrentUserDto;
import dev.mfa.incident.service.CurrentUserService;
import io.quarkus.security.Authenticated;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

/** The authenticated user, for the UI (A-0010, P-0019). */
@Path("/v1/me")
@Produces(MediaType.APPLICATION_JSON)
@Tag(name = "User")
public class CurrentUserResource {

  private final CurrentUserService users;

  public CurrentUserResource(CurrentUserService users) {
    this.users = users;
  }

  @GET
  @Authenticated
  @Operation(summary = "Name and incident roles of the authenticated user")
  @APIResponse(responseCode = "200", description = "The user")
  @APIResponse(
      responseCode = "401",
      description = "Not authenticated",
      content = @Content(mediaType = ProblemDetail.MEDIA_TYPE))
  public CurrentUserDto me() {
    return users.currentUser();
  }
}
