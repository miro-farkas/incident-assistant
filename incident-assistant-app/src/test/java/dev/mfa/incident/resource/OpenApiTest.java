package dev.mfa.incident.resource;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * The generated OpenAPI document describes the approval API as A-0006 and P-0009 define it, and the
 * web UI builds against the same document.
 */
@QuarkusTest
class OpenApiTest {

  private static final String LIST = "/api/v1/incidents";
  private static final String DETAIL = "/api/v1/incidents/{id}";
  private static final String DECISION = "/api/v1/incidents/{id}/decision";
  private static final Path UI_SNAPSHOT = Path.of("src/main/webui/openapi/incident-assistant.json");

  @Inject ObjectMapper objectMapper;

  private JsonNode document;

  @BeforeEach
  void loadDocument() throws Exception {
    String json =
        given()
            .queryParam("format", "json")
            .when()
            .get("/q/openapi")
            .then()
            .statusCode(200)
            .extract()
            .asString();
    document = objectMapper.readTree(json);
  }

  @Test
  void documentsAllOperationsWithBearerSecurity() {
    assertThat(document.at("/paths").has(LIST)).isTrue();
    assertThat(operation(LIST, "get").isMissingNode()).isFalse();
    assertThat(operation(DETAIL, "get").isMissingNode()).isFalse();
    assertThat(operation(DECISION, "post").isMissingNode()).isFalse();
    assertThat(document.at("/components/securitySchemes/SecurityScheme/scheme").asText())
        .isEqualTo("bearer");
    for (JsonNode operation :
        List.of(operation(LIST, "get"), operation(DETAIL, "get"), operation(DECISION, "post"))) {
      assertThat(operation.get("security")).as("security of %s", operation).isNotNull();
    }
  }

  @Test
  void decisionDocumentsPreconditionsAndProblemResponses() {
    JsonNode decision = operation(DECISION, "post");

    assertThat(fieldNames(decision.get("responses")))
        .contains("200", "400", "401", "403", "404", "409", "412", "428");
    for (String status : List.of("409", "412", "428")) {
      assertThat(decision.at("/responses/" + status + "/content").has("application/problem+json"))
          .as("problem body of %s", status)
          .isTrue();
    }
    assertThat(decision.at("/responses/200/headers").has("ETag")).isTrue();
    List<String> headers = new ArrayList<>();
    decision.get("parameters").forEach(p -> headers.add(p.get("name").asText()));
    assertThat(headers).contains("If-Match");
  }

  @Test
  void streamDocumentsEventStreamAndLimit() {
    JsonNode stream = operation("/api/v1/incidents/stream", "get");

    assertThat(stream.at("/responses/200/content").has("text/event-stream")).isTrue();
    assertThat(stream.at("/responses/503/headers").has("Retry-After")).isTrue();
    assertThat(stream.get("security")).isNotNull();
  }

  @Test
  void detailDocumentsEtag() {
    assertThat(operation(DETAIL, "get").at("/responses/200/headers").has("ETag")).isTrue();
  }

  @Test
  void currentUserIsDocumented() {
    JsonNode me = operation("/api/v1/me", "get");

    assertThat(me.get("security")).isNotNull();
    assertThat(fieldNames(me.get("responses"))).contains("200", "401");
  }

  /**
   * The web UI generates its API types from the checked-in snapshot (P-0019), so an API change has
   * to reach the snapshot to reach the UI build. On a difference the test writes the current
   * document to the snapshot and fails; review the diff and run the build again.
   */
  @Test
  void webUiSnapshotMatchesTheDocument() throws Exception {
    boolean matches =
        Files.exists(UI_SNAPSHOT) && objectMapper.readTree(UI_SNAPSHOT.toFile()).equals(document);
    if (!matches) {
      Files.createDirectories(UI_SNAPSHOT.getParent());
      Files.writeString(
          UI_SNAPSHOT,
          objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(document) + "\n");
    }
    assertThat(matches)
        .as(
            "%s was out of date and has been rewritten; review the change and build again",
            UI_SNAPSHOT)
        .isTrue();
  }

  private JsonNode operation(String path, String method) {
    return document.at("/paths").path(path).path(method);
  }

  private static List<String> fieldNames(JsonNode node) {
    List<String> names = new ArrayList<>();
    node.fieldNames().forEachRemaining(names::add);
    return names;
  }
}
