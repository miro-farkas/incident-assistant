package dev.mfa.incident.resource;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.mfa.incident.dto.IncidentDto;
import dev.mfa.incident.testsupport.WorkflowDriver;
import dev.mfa.incident.testsupport.WorkflowTables;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import io.quarkus.test.keycloak.client.KeycloakTestClient;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Runs against a real Keycloak started with the checked-in realm (the one the compose Keycloak
 * imports), so roles, client and users of {@code keycloak/realm-incident-assistant.json} and the
 * mapping of token claims are verified end to end.
 */
@QuarkusTest
@TestProfile(KeycloakRealmTest.RealKeycloak.class)
class KeycloakRealmTest {

  private static final String CLIENT = "incident-assistant";
  private static final String CLIENT_SECRET = "incident-assistant-local-secret";
  private static final String INCIDENTS = "/api/v1/incidents";

  public static class RealKeycloak implements QuarkusTestProfile {
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
          "quarkus.oidc.credentials.secret", CLIENT_SECRET);
    }
  }

  private final KeycloakTestClient keycloak = new KeycloakTestClient();

  @Inject WorkflowDriver workflow;
  @Inject WorkflowTables tables;
  @Inject ObjectMapper objectMapper;

  @Test
  void viewerReadsButCannotDecide() {
    IncidentDto pending = workflow.awaitingApproval();
    String token = token("viewer");

    given().auth().oauth2(token).when().get(INCIDENTS + "/" + pending.id()).then().statusCode(200);
    given()
        .auth()
        .oauth2(token)
        .contentType(ContentType.JSON)
        .header("If-Match", "\"" + pending.version() + "\"")
        .body("{\"decision\": \"APPROVE\"}")
        .when()
        .post(INCIDENTS + "/" + pending.id() + "/decision")
        .then()
        .statusCode(403)
        .contentType(ProblemDetail.MEDIA_TYPE);
  }

  @Test
  void approverDecisionStoresTokenSubject() throws Exception {
    IncidentDto pending = workflow.awaitingApproval();
    String token = token("approver");
    String subject = claim(token, "sub");

    given()
        .auth()
        .oauth2(token)
        .contentType(ContentType.JSON)
        .header("If-Match", "\"" + pending.version() + "\"")
        .body("{\"decision\": \"REJECT\", \"comment\": \"Wrong service\"}")
        .when()
        .post(INCIDENTS + "/" + pending.id() + "/decision")
        .then()
        .statusCode(200)
        .body("state", equalTo("REJECTED"))
        .body("decision.approverSubject", equalTo(subject));

    assertThat(subject).isNotBlank().isNotEqualTo("approver");
    assertThat(tables.decisions(pending.id()))
        .singleElement()
        .satisfies(row -> assertThat(row.approverSubject()).isEqualTo(subject));
  }

  @Test
  void invalidTokenIsUnauthorized() {
    given()
        .auth()
        .oauth2("not-a-token")
        .when()
        .get(INCIDENTS)
        .then()
        .statusCode(401)
        .contentType(ProblemDetail.MEDIA_TYPE)
        .header("WWW-Authenticate", "Bearer");
  }

  private String token(String user) {
    return keycloak.getAccessToken(user, user, CLIENT, CLIENT_SECRET);
  }

  private String claim(String token, String name) throws Exception {
    String payload = token.split("\\.")[1];
    JsonNode claims =
        objectMapper.readTree(
            new String(Base64.getUrlDecoder().decode(payload), StandardCharsets.UTF_8));
    return claims.get(name).asText();
  }
}
