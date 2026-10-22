package dev.mfa.incident.resource;

import io.quarkus.oidc.IdTokenCredential;
import io.quarkus.security.identity.CurrentIdentityAssociation;
import io.smallrye.mutiny.Uni;
import jakarta.ws.rs.HttpMethod;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.Response;
import java.util.Set;
import org.jboss.resteasy.reactive.server.ServerRequestFilter;

/**
 * CSRF protection for browser sessions (P-0019, A-0010): a state-changing request authenticated by
 * the session cookie of the code flow must carry {@value #HEADER}: {@value #VALUE}, which the UI
 * sets and a cross-site form cannot; otherwise it is rejected with {@code 403}. Requests with a
 * bearer token are not affected: a browser never adds one on its own.
 */
public class CsrfHeaderFilter {

  static final String HEADER = "X-Requested-By";
  static final String VALUE = "incident-ui";

  private static final Set<String> SAFE_METHODS =
      Set.of(HttpMethod.GET, HttpMethod.HEAD, HttpMethod.OPTIONS);

  private final CurrentIdentityAssociation identities;

  public CsrfHeaderFilter(CurrentIdentityAssociation identities) {
    this.identities = identities;
  }

  /**
   * The identity is resolved only for state-changing requests without the header. An identity from
   * the code flow carries the ID token as credential; one from a bearer token does not.
   */
  @ServerRequestFilter
  public Uni<Void> requireHeaderOnSessionRequests(ContainerRequestContext request) {
    if (SAFE_METHODS.contains(request.getMethod())
        || VALUE.equals(request.getHeaderString(HEADER))) {
      return Uni.createFrom().voidItem();
    }
    return identities
        .getDeferredIdentity()
        .invoke(
            identity -> {
              if (identity.getCredential(IdTokenCredential.class) != null) {
                throw new ProblemException(
                    Response.Status.FORBIDDEN,
                    "Requests from a browser session must carry the header " + HEADER);
              }
            })
        .replaceWithVoid();
  }
}
