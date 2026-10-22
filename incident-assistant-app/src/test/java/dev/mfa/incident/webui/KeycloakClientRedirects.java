package dev.mfa.incident.webui;

import static io.restassured.RestAssured.given;

import dev.mfa.incident.testsupport.RealKeycloakProfile;
import io.quarkus.test.keycloak.client.KeycloakTestClient;
import io.restassured.http.ContentType;
import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The checked-in realm allows redirects to the dev mode port 8080 only; a browser test runs on a
 * random port. This adds the test application's address to the client through the admin API of the
 * test Keycloak, the way an administrator would register another deployment.
 */
final class KeycloakClientRedirects {

  private static final String POST_LOGOUT_URIS = "post.logout.redirect.uris";
  private static final String URI_SEPARATOR = "##";

  private KeycloakClientRedirects() {}

  @SuppressWarnings("unchecked")
  static void allow(URI application) {
    KeycloakTestClient keycloak = new KeycloakTestClient();
    String adminToken = keycloak.getAdminAccessToken();
    String clients =
        keycloak.getAuthServerBaseUrl() + "/admin/realms/" + RealKeycloakProfile.REALM + "/clients";
    String pattern = application.resolve("/").toString() + "*";

    Map<String, Object> client =
        given()
            .auth()
            .oauth2(adminToken)
            .queryParam("clientId", RealKeycloakProfile.CLIENT)
            .when()
            .get(clients)
            .then()
            .statusCode(200)
            .extract()
            .jsonPath()
            .getMap("[0]");

    List<String> redirects = new ArrayList<>((List<String>) client.get("redirectUris"));
    if (redirects.contains(pattern)) {
      return;
    }
    redirects.add(pattern);
    Map<String, Object> attributes = new HashMap<>((Map<String, Object>) client.get("attributes"));
    attributes.merge(POST_LOGOUT_URIS, pattern, (old, added) -> old + URI_SEPARATOR + added);
    client.put("redirectUris", redirects);
    client.put("attributes", attributes);

    given()
        .auth()
        .oauth2(adminToken)
        .contentType(ContentType.JSON)
        .body(client)
        .when()
        .put(clients + "/" + client.get("id"))
        .then()
        .statusCode(204);
  }
}
