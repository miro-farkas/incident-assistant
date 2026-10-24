package dev.mfa.incident.notification.client;

import io.quarkus.oidc.client.filter.OidcClientFilter;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import java.util.UUID;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

/**
 * The approval API of incident-assistant-app (A-0006), called with a token of the client {@code
 * notification-service} from the client credentials flow (role {@code incident-viewer}, A-0011).
 * Address and timeouts: {@code quarkus.rest-client.incident-api.*}.
 */
@RegisterRestClient(configKey = "incident-api")
@OidcClientFilter
@Path("/api/v1/incidents")
@Produces(MediaType.APPLICATION_JSON)
public interface IncidentApiClient {

  @GET
  @Path("/{id}")
  IncidentDetailResponse get(@PathParam("id") UUID id);
}
