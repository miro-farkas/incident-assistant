package dev.mfa.incident.resource;

import static dev.mfa.incident.testsupport.WorkflowFixtures.alert;
import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;

import dev.mfa.incident.dto.AlertIngestionResult;
import dev.mfa.incident.dto.AlertMessage;
import dev.mfa.incident.dto.DecisionDto;
import dev.mfa.incident.dto.IncidentDto;
import dev.mfa.incident.service.AlertIngestionService;
import dev.mfa.incident.testsupport.WorkflowDriver;
import dev.mfa.incident.testsupport.WorkflowTables;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import io.restassured.response.ValidatableResponse;
import jakarta.inject.Inject;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

@QuarkusTest
class IncidentResourceTest {

  private static final String INCIDENTS = "/api/v1/incidents";
  private static final String PROBLEM = "application/problem+json";
  private static final String APPROVER = "approver-subject";
  private static final String APPROVE = "{\"decision\": \"APPROVE\", \"comment\": \"Looks safe\"}";

  @Inject WorkflowDriver workflow;
  @Inject WorkflowTables tables;
  @Inject AlertIngestionService ingestion;

  @Test
  @TestSecurity(user = "viewer-subject", roles = "incident-viewer")
  void listFiltersByStateNewestFirstWithPaging() {
    IncidentDto older = workflow.awaitingApproval();
    IncidentDto newer = workflow.awaitingApproval();

    List<String> ids =
        given()
            .queryParam("state", "AWAITING_APPROVAL")
            .queryParam("size", 100)
            .when()
            .get(INCIDENTS)
            .then()
            .statusCode(200)
            .contentType(ContentType.JSON)
            .body("page", equalTo(0))
            .body("size", equalTo(100))
            .body("items.state", everyItem(equalTo("AWAITING_APPROVAL")))
            .extract()
            .jsonPath()
            .getList("items.id", String.class);
    assertThat(ids).contains(newer.id().toString(), older.id().toString());
    assertThat(ids.indexOf(newer.id().toString())).isLessThan(ids.indexOf(older.id().toString()));

    ValidatableResponse firstPage =
        given()
            .queryParam("state", "AWAITING_APPROVAL")
            .queryParam("state", "TRIAGED")
            .queryParam("size", 1)
            .when()
            .get(INCIDENTS)
            .then()
            .statusCode(200)
            .body("items.size()", equalTo(1))
            .body("items[0].id", equalTo(newer.id().toString()));
    long totalItems = firstPage.extract().jsonPath().getLong("totalItems");
    assertThat(totalItems).isGreaterThanOrEqualTo(2);
    assertThat(firstPage.extract().jsonPath().getLong("totalPages")).isEqualTo(totalItems);
  }

  @Test
  @TestSecurity(user = "viewer-subject", roles = "incident-viewer")
  void listRejectsUnknownStateAndInvalidPaging() {
    problem(given().queryParam("state", "SLEEPING").when().get(INCIDENTS), 400)
        .body("detail", containsString("AWAITING_APPROVAL"))
        .body("detail", not(containsString("SLEEPING")));
    problem(given().queryParam("size", 101).when().get(INCIDENTS), 400)
        .body("violations.field", hasItem("list.size"));
    problem(given().queryParam("page", -1).when().get(INCIDENTS), 400)
        .body("violations.field", hasItem("list.page"));
  }

  @Test
  void requestWithoutTokenIsUnauthorized() {
    problem(given().when().get(INCIDENTS), 401).header("WWW-Authenticate", "Bearer");
    problem(
        given()
            .contentType(ContentType.JSON)
            .body(APPROVE)
            .header("If-Match", "\"0\"")
            .when()
            .post(INCIDENTS + "/" + UUID.randomUUID() + "/decision"),
        401);
  }

  @Test
  @TestSecurity(user = "someone", roles = "knowledge-admin")
  void userWithoutIncidentRoleIsForbidden() {
    problem(given().when().get(INCIDENTS), 403);
  }

  @Test
  @TestSecurity(user = "approver-subject", roles = "incident-approver")
  void approverCanReadWithoutViewerRole() {
    given().when().get(INCIDENTS).then().statusCode(200);
  }

  @Test
  @TestSecurity(user = "viewer-subject", roles = "incident-viewer")
  void detailShowsAlertStepsOccurrencesAndVersionAsEtag() {
    IncidentDto pending = workflow.awaitingApproval();

    given()
        .when()
        .get(INCIDENTS + "/" + pending.id())
        .then()
        .statusCode(200)
        .header("ETag", "\"" + pending.version() + "\"")
        .body("id", equalTo(pending.id().toString()))
        .body("state", equalTo("AWAITING_APPROVAL"))
        .body("version", equalTo((int) pending.version()))
        .body("alert.alertname", equalTo("HighErrorRate"))
        .body("alert.value", equalTo(0.27f))
        .body("occurrenceCount", equalTo(0))
        .body("steps.type", equalTo(List.of("TRIAGE", "DIAGNOSIS", "PROPOSAL")))
        .body("triage.severity", equalTo("HIGH"))
        .body("triage.category", equalTo("DEPLOYMENT"))
        .body("triage.affectedService", equalTo("checkout"))
        .body("triage.summary", containsString("after a release"))
        .body("steps[2].fromState", equalTo("DIAGNOSED"))
        .body("steps[2].toState", equalTo("AWAITING_APPROVAL"))
        .body("steps[2].output.result", equalTo("ok"))
        .body("steps[2].promptVersion", equalTo("3"))
        .body("decision", nullValue());
  }

  @Test
  @TestSecurity(user = "viewer-subject", roles = "incident-viewer")
  void listCountsRepeatedAlertsPerIncident() {
    AlertMessage noisy = alert();
    UUID noisyId = ingestion.ingest(noisy).incidentId();
    ingestion.ingest(noisy);
    ingestion.ingest(noisy);
    UUID quietId = ingestion.ingest(alert()).incidentId();

    var page =
        given()
            .queryParam("state", "RECEIVED")
            .queryParam("size", 100)
            .when()
            .get(INCIDENTS)
            .then()
            .statusCode(200)
            .extract()
            .jsonPath();

    assertThat(page.getLong("items.find { it.id == '" + noisyId + "' }.occurrenceCount"))
        .isEqualTo(2);
    assertThat(page.getLong("items.find { it.id == '" + quietId + "' }.occurrenceCount")).isZero();
  }

  @Test
  @TestSecurity(user = "viewer-subject", roles = "incident-viewer")
  void detailCountsRepeatedAlerts() {
    AlertMessage alert = alert();
    UUID incidentId = ingestion.ingest(alert).incidentId();
    AlertIngestionResult repeated = ingestion.ingest(alert);
    assertThat(repeated).isInstanceOf(AlertIngestionResult.OccurrenceRecorded.class);

    given()
        .when()
        .get(INCIDENTS + "/" + incidentId)
        .then()
        .statusCode(200)
        .body("occurrenceCount", equalTo(1))
        .body("alert.fingerprint", equalTo(alert.fingerprint()))
        .body("steps.size()", equalTo(0))
        .body("triage", nullValue());
  }

  @Test
  @TestSecurity(user = "viewer-subject", roles = "incident-viewer")
  void unknownOrMalformedIncidentIdIsNotFound() {
    problem(given().when().get(INCIDENTS + "/" + UUID.randomUUID()), 404);
    problem(given().when().get(INCIDENTS + "/not-a-uuid"), 404);
  }

  @Test
  @TestSecurity(user = APPROVER, roles = "incident-approver")
  void approverDecidesWithIfMatchAndGetsNewVersion() {
    IncidentDto pending = workflow.awaitingApproval();

    given()
        .contentType(ContentType.JSON)
        .header("If-Match", "\"" + pending.version() + "\"")
        .body(APPROVE)
        .when()
        .post(INCIDENTS + "/" + pending.id() + "/decision")
        .then()
        .statusCode(200)
        .header("ETag", "\"" + (pending.version() + 1) + "\"")
        .body("state", equalTo("APPROVED"))
        .body("decision.decision", equalTo("APPROVE"))
        .body("decision.approverSubject", equalTo(APPROVER))
        .body("decision.comment", equalTo("Looks safe"))
        .body("decision.decidedAt", notNullValue());

    assertThat(tables.decisions(pending.id()))
        .singleElement()
        .satisfies(row -> assertThat(row.approverSubject()).isEqualTo(APPROVER));
  }

  @Test
  @TestSecurity(user = "viewer-subject", roles = "incident-viewer")
  void viewerCannotDecide() {
    IncidentDto pending = workflow.awaitingApproval();

    problem(decide(pending.id(), "\"" + pending.version() + "\"", APPROVE), 403);
    assertThat(tables.decisions(pending.id())).isEmpty();
  }

  @Test
  @TestSecurity(user = APPROVER, roles = "incident-approver")
  void decisionRequiresCurrentVersionInIfMatch() {
    IncidentDto pending = workflow.awaitingApproval();
    String path = INCIDENTS + "/" + pending.id() + "/decision";

    problem(given().contentType(ContentType.JSON).body(APPROVE).when().post(path), 428);
    problem(decide(pending.id(), "*", APPROVE), 428);
    problem(decide(pending.id(), "W/\"" + pending.version() + "\"", APPROVE), 400);
    problem(decide(pending.id(), String.valueOf(pending.version()), APPROVE), 400);
    problem(decide(pending.id(), "\"" + (pending.version() - 1) + "\"", APPROVE), 412);

    assertThat(tables.decisions(pending.id())).isEmpty();
  }

  @Test
  @TestSecurity(user = APPROVER, roles = "incident-approver")
  void decisionOutsideAwaitingApprovalIsAConflict() {
    IncidentDto triaged = workflow.triaged();

    problem(decide(triaged.id(), "\"" + triaged.version() + "\"", APPROVE), 409)
        .body("detail", containsString("TRIAGED"));
  }

  @Test
  @TestSecurity(user = APPROVER, roles = "incident-approver")
  void invalidDecisionBodyIsRejected() {
    IncidentDto pending = workflow.awaitingApproval();
    String version = "\"" + pending.version() + "\"";
    String tooLong = "x".repeat(DecisionDto.MAX_COMMENT_LENGTH + 1);

    problem(decide(pending.id(), version, "{\"comment\": \"no decision\"}"), 400)
        .body("violations.field", hasItem("decide.decision.decision"));
    problem(
            decide(
                pending.id(),
                version,
                "{\"decision\": \"REJECT\", \"comment\": \"" + tooLong + "\"}"),
            400)
        .body("violations.field", hasItem("decide.decision.comment"));
    problem(decide(pending.id(), version, "{\"decision\": \"MAYBE\"}"), 400)
        .body("detail", not(containsString("MAYBE")));
    problem(decide(pending.id(), version, "{\"decision\": "), 400);
    problem(decide(pending.id(), version, ""), 400)
        .body("violations.field", hasItem("decide.decision"));
    problem(
        given()
            .header("If-Match", version)
            .when()
            .post(INCIDENTS + "/" + pending.id() + "/decision"),
        415);

    assertThat(tables.decisions(pending.id())).isEmpty();
  }

  private static Response decide(UUID id, String ifMatch, String body) {
    return given()
        .contentType(ContentType.JSON)
        .header("If-Match", ifMatch)
        .body(body)
        .when()
        .post(INCIDENTS + "/" + id + "/decision");
  }

  private static ValidatableResponse problem(Response response, int status) {
    return response
        .then()
        .statusCode(status)
        .contentType(PROBLEM)
        .body("type", equalTo("about:blank"))
        .body("status", equalTo(status))
        .body("title", notNullValue())
        .body("detail", notNullValue());
  }
}
