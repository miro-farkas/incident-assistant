package dev.mfa.incident.resource;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;

import dev.mfa.incident.repository.KnowledgeDocumentRepository;
import dev.mfa.incident.repository.KnowledgeDocumentRepository.IngestionLock;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

/** The admin ingestion endpoint (A-0013): role {@code knowledge-admin}, 409 while one runs. */
@QuarkusTest
class KnowledgeResourceTest {

  private static final String INGESTION = "/api/v1/knowledge/ingestion";

  @Inject KnowledgeDocumentRepository documents;

  @Test
  @TestSecurity(user = "admin-subject", roles = "knowledge-admin")
  void knowledgeAdminIngestsTheKnowledgeDirectory() {
    given().when().post(INGESTION).then().statusCode(200).body("skipped", empty());
    given().when().post(INGESTION).then().statusCode(200).body("unchanged", equalTo(5));
  }

  @Test
  @TestSecurity(user = "admin-subject", roles = "knowledge-admin")
  void concurrentRunIsAConflict() {
    try (IngestionLock held = documents.tryLockIngestion().orElseThrow()) {
      given().when().post(INGESTION).then().statusCode(409).contentType(ProblemDetail.MEDIA_TYPE);
    }
  }

  @Test
  @TestSecurity(
      user = "approver-subject",
      roles = {"incident-approver", "incident-viewer"})
  void incidentRolesMayNotIngest() {
    given().when().post(INGESTION).then().statusCode(403).contentType(ProblemDetail.MEDIA_TYPE);
  }

  @Test
  void ingestionRequiresAuthentication() {
    given().when().post(INGESTION).then().statusCode(401);
  }
}
