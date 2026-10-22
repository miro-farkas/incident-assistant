package dev.mfa.incident.testsupport;

import io.quarkus.oidc.AccessTokenCredential;
import io.quarkus.oidc.IdTokenCredential;
import io.quarkus.security.identity.AuthenticationRequestContext;
import io.quarkus.security.identity.SecurityIdentity;
import io.quarkus.security.identity.SecurityIdentityAugmentor;
import io.quarkus.security.runtime.QuarkusSecurityIdentity;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * Makes a {@code @TestSecurity} identity look like one from the browser code flow, which carries
 * the ID token as credential: {@code @TestSecurity(..., augmentors =
 * BrowserSessionAugmentor.class)}. As a bean it also sees identities from real tokens (profiles
 * with Keycloak); those carry an access token credential and stay unchanged.
 */
@ApplicationScoped
public class BrowserSessionAugmentor implements SecurityIdentityAugmentor {

  @Override
  public Uni<SecurityIdentity> augment(
      SecurityIdentity identity, AuthenticationRequestContext context) {
    if (identity.isAnonymous() || identity.getCredential(AccessTokenCredential.class) != null) {
      return Uni.createFrom().item(identity);
    }
    return Uni.createFrom()
        .item(
            QuarkusSecurityIdentity.builder(identity)
                .addCredential(new IdTokenCredential("test-id-token"))
                .build());
  }
}
