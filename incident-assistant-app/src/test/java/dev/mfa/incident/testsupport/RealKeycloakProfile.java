package dev.mfa.incident.testsupport;

import io.quarkus.test.junit.QuarkusTestProfile;
import java.util.Map;

/**
 * A real Keycloak started with the checked-in realm (the one the compose Keycloak imports), and the
 * web UI built and served by Quinoa. Shared by the tests that need real tokens or a real browser
 * login, so the application starts once for all of them.
 */
public class RealKeycloakProfile implements QuarkusTestProfile {

  public static final String REALM = "incident-assistant";
  public static final String CLIENT = "incident-assistant";
  public static final String CLIENT_SECRET = "incident-assistant-local-secret";

  @Override
  public Map<String, String> getConfigOverrides() {
    return Map.of(
        // Keycloak's first start (database init) can exceed the 60 s default, and creating the
        // realm with its users through the admin API the 4 s default.
        "quarkus.devservices.timeout", "3M",
        "quarkus.keycloak.devservices.web-client-timeout", "60S",
        "quarkus.keycloak.devservices.enabled", "true",
        "quarkus.keycloak.devservices.realm-path", "../keycloak/realm-incident-assistant.json",
        "quarkus.oidc.tenant-enabled", "true",
        "quarkus.oidc.credentials.secret", CLIENT_SECRET,
        // Quinoa is off in tests by default; the browser test needs the built UI.
        "quarkus.quinoa", "true");
  }
}
