package dev.mfa.incident.webui;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.nullValue;

import dev.mfa.incident.dto.IncidentDto;
import dev.mfa.incident.testsupport.BrowserSessionAugmentor;
import dev.mfa.incident.testsupport.WorkflowDriver;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.restassured.http.ContentType;
import io.restassured.response.ValidatableResponse;
import jakarta.inject.Inject;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Browser-facing security of P-0019 and A-0010: the CSRF header for session requests, {@code GET
 * /api/v1/me} and the security headers. The code flow itself runs against a real Keycloak in {@code
 * IncidentUiEndToEndTest}.
 */
@QuarkusTest
class BrowserSessionSecurityTest {

  private static final String INCIDENTS = "/api/v1/incidents";
  private static final String ME = "/api/v1/me";
  private static final String APPROVE = "{\"decision\": \"APPROVE\"}";
  private static final String CSP = "Content-Security-Policy";
  private static final String PROBLEM_DETAILS = "application/problem+json";
  // What the UI sends (client.ts); the service must accept exactly this.
  private static final String CSRF_HEADER = "X-Requested-By";
  private static final String CSRF_VALUE = "incident-ui";

  @Inject WorkflowDriver workflow;

  @Test
  @TestSecurity(
      user = "approver-subject",
      roles = "incident-approver",
      augmentors = BrowserSessionAugmentor.class)
  void sessionDecisionWithoutCsrfHeaderIsForbidden() {
    IncidentDto pending = workflow.awaitingApproval();

    decide(pending, null)
        .statusCode(403)
        .contentType(PROBLEM_DETAILS)
        .body("detail", containsString(CSRF_HEADER));
    decide(pending, "something-else").statusCode(403);
    decide(pending, CSRF_VALUE).statusCode(200).body("state", equalTo("APPROVED"));
  }

  @Test
  @TestSecurity(user = "approver-subject", roles = "incident-approver")
  void tokenDecisionNeedsNoCsrfHeader() {
    decide(workflow.awaitingApproval(), null).statusCode(200);
  }

  @Test
  @TestSecurity(
      user = "viewer-subject",
      roles = {"incident-viewer", "offline_access", "default-roles-incident-assistant"})
  void meReturnsNameAndIncidentRolesOnly() {
    given()
        .when()
        .get(ME)
        .then()
        .statusCode(200)
        .body("name", equalTo("viewer-subject"))
        .body("roles", equalTo(List.of("incident-viewer")));
  }

  @Test
  void meRequiresAuthentication() {
    given().when().get(ME).then().statusCode(401).contentType(PROBLEM_DETAILS);
  }

  @Test
  void responsesCarryAStrictContentSecurityPolicy() {
    for (String path : List.of(ME, "/", "/incidents/some-id")) {
      given()
          .when()
          .get(path)
          .then()
          .header(CSP, containsString("default-src 'none'"))
          .header(CSP, containsString("script-src 'self';"))
          .header(CSP, containsString("frame-ancestors 'none'"))
          .header("X-Content-Type-Options", "nosniff")
          .header("Referrer-Policy", "no-referrer");
    }
    given().when().get("/q/health/live").then().header(CSP, nullValue());
  }

  private ValidatableResponse decide(IncidentDto incident, String csrfHeader) {
    var request =
        given()
            .contentType(ContentType.JSON)
            .header("If-Match", "\"" + incident.version() + "\"")
            .body(APPROVE);
    if (csrfHeader != null) {
      request.header(CSRF_HEADER, csrfHeader);
    }
    return request.when().post(INCIDENTS + "/" + incident.id() + "/decision").then();
  }
}
