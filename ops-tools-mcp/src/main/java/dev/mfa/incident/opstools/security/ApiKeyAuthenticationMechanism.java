package dev.mfa.incident.opstools.security;

import dev.mfa.incident.opstools.config.OpsToolsConfig;
import io.quarkus.security.AuthenticationFailedException;
import io.quarkus.security.identity.IdentityProviderManager;
import io.quarkus.security.identity.SecurityIdentity;
import io.quarkus.security.identity.request.AuthenticationRequest;
import io.quarkus.security.runtime.QuarkusPrincipal;
import io.quarkus.security.runtime.QuarkusSecurityIdentity;
import io.quarkus.vertx.http.runtime.security.ChallengeData;
import io.quarkus.vertx.http.runtime.security.HttpAuthenticationMechanism;
import io.quarkus.vertx.http.runtime.security.HttpCredentialTransport;
import io.smallrye.mutiny.Uni;
import io.vertx.ext.web.RoutingContext;
import jakarta.enterprise.context.ApplicationScoped;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Set;

/**
 * Authenticates clients of the action endpoint by the key in header {@code X-Api-Key} (A-0005). A
 * request with the configured key gets role {@value #ACTIONS_ROLE}; a request without the header
 * stays anonymous; a request with a wrong key is rejected with 401.
 *
 * <p>The HTTP security SPI is reactive, so this class returns Mutiny {@link Uni}s although the rest
 * of the code is imperative (P-0002); the work is a header comparison and never blocks.
 */
@ApplicationScoped
public class ApiKeyAuthenticationMechanism implements HttpAuthenticationMechanism {

  public static final String HEADER = "X-Api-Key";
  public static final String ACTIONS_ROLE = "ops-actions";
  static final String PRINCIPAL = "ops-actions-client";

  private final byte[] expectedKey;

  public ApiKeyAuthenticationMechanism(OpsToolsConfig config) {
    this.expectedKey = config.actions().apiKey().getBytes(StandardCharsets.UTF_8);
  }

  @Override
  public Uni<SecurityIdentity> authenticate(
      RoutingContext context, IdentityProviderManager identityProviderManager) {
    String presented = context.request().getHeader(HEADER);
    if (presented == null) {
      return Uni.createFrom().nullItem();
    }
    // Constant-time comparison, so the response time does not reveal how much of a key matched.
    if (!MessageDigest.isEqual(expectedKey, presented.getBytes(StandardCharsets.UTF_8))) {
      return Uni.createFrom().failure(new AuthenticationFailedException("Invalid API key"));
    }
    return Uni.createFrom()
        .item(
            QuarkusSecurityIdentity.builder()
                .setPrincipal(new QuarkusPrincipal(PRINCIPAL))
                .addRole(ACTIONS_ROLE)
                .build());
  }

  @Override
  public Uni<ChallengeData> getChallenge(RoutingContext context) {
    return Uni.createFrom()
        .item(new ChallengeData(401, "WWW-Authenticate", "ApiKey header=\"" + HEADER + "\""));
  }

  @Override
  public Set<Class<? extends AuthenticationRequest>> getCredentialTypes() {
    return Set.of();
  }

  @Override
  public Uni<HttpCredentialTransport> getCredentialTransport(RoutingContext context) {
    return Uni.createFrom()
        .item(new HttpCredentialTransport(HttpCredentialTransport.Type.OTHER_HEADER, HEADER));
  }
}
