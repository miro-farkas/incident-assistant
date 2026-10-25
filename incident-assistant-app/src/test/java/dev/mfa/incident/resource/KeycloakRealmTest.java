package dev.mfa.incident.resource;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.mfa.incident.dto.IncidentDto;
import dev.mfa.incident.testsupport.RealKeycloakProfile;
import dev.mfa.incident.testsupport.WorkflowDriver;
import dev.mfa.incident.testsupport.WorkflowTables;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.quarkus.test.keycloak.client.KeycloakTestClient;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Runs against a real Keycloak started with the checked-in realm (the one the compose Keycloak
 * imports), so roles, client and users of {@code keycloak/realm-incident-assistant.json} and the
 * mapping of token claims are verified end to end.
 */
@QuarkusTest
@TestProfile(RealKeycloakProfile.class)
class KeycloakRealmTest {

  private static final String INCIDENTS = "/api/v1/incidents";

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
  void meShowsTheProfileNameAndOnlyIncidentRoles() {
    given()
        .auth()
        .oauth2(token("approver"))
        .when()
        .get("/api/v1/me")
        .then()
        .statusCode(200)
        .body("name", equalTo("Arno Approver"))
        .body("roles", equalTo(List.of("incident-approver", "incident-viewer")));
  }

  /**
   * The service account of notification-service (A-0011) reads incidents with a client credentials
   * token and cannot decide.
   */
  @Test
  void notificationServiceAccountReadsButCannotDecide() {
    IncidentDto pending = workflow.awaitingApproval();
    String token =
        keycloak.getClientAccessToken("notification-service", "notification-service-local-secret");

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
        .statusCode(403);
  }

  @Test
  void knowledgeAdminRunsTheIngestion() {
    given()
        .auth()
        .oauth2(token("knowledge-admin"))
        .when()
        .post("/api/v1/knowledge/ingestion")
        .then()
        .statusCode(200);
    given()
        .auth()
        .oauth2(token("approver"))
        .when()
        .post("/api/v1/knowledge/ingestion")
        .then()
        .statusCode(403);
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
    return keycloak.getAccessToken(
        user, user, RealKeycloakProfile.CLIENT, RealKeycloakProfile.CLIENT_SECRET);
  }

  private String claim(String token, String name) throws Exception {
    String payload = token.split("\\.")[1];
    JsonNode claims =
        objectMapper.readTree(
            new String(Base64.getUrlDecoder().decode(payload), StandardCharsets.UTF_8));
    return claims.get(name).asText();
  }
}
