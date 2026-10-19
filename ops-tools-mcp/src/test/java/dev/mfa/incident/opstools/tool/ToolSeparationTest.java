package dev.mfa.incident.opstools.tool;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;

import dev.mfa.incident.opstools.testsupport.McpClients;
import io.quarkiverse.mcp.server.JsonRpcErrorCodes;
import io.quarkiverse.mcp.server.test.McpAssured.ConnectFailureResponse;
import io.quarkiverse.mcp.server.test.McpAssured.McpStreamableTestClient;
import io.quarkiverse.mcp.server.test.McpAssured.ToolInfo;
import io.quarkus.test.junit.QuarkusTest;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

/**
 * Read and action tools are served on separate endpoints, and only a client with the API key
 * reaches the action tools (P-0006, A-0005).
 */
@QuarkusTest
class ToolSeparationTest {

  @Test
  void readEndpointListsExactlyTheReadTools() {
    McpStreamableTestClient client = McpClients.read();
    try {
      client
          .when()
          .toolsList(
              page -> {
                assertThat(page.tools())
                    .extracting(ToolInfo::name)
                    .containsExactlyInAnyOrderElementsOf(ToolGroups.READ_TOOLS);
                assertThat(page.tools())
                    .allSatisfy(
                        tool ->
                            assertThat(tool.annotations().orElseThrow().readOnlyHint()).isTrue());
              })
          .thenAssertResults();
    } finally {
      client.disconnect();
    }
  }

  @Test
  void actionEndpointListsExactlyTheActionTools() {
    McpStreamableTestClient client = McpClients.actions();
    try {
      client
          .when()
          .toolsList(
              page ->
                  assertThat(page.tools())
                      .extracting(ToolInfo::name)
                      .containsExactlyInAnyOrderElementsOf(ToolGroups.ACTION_TOOLS))
          .thenAssertResults();
    } finally {
      client.disconnect();
    }
  }

  @Test
  void toolGroupsAreDisjoint() {
    assertThat(ToolGroups.READ_TOOLS).doesNotContainAnyElementsOf(ToolGroups.ACTION_TOOLS);
  }

  @Test
  void actionToolCannotBeCalledThroughTheReadEndpoint() {
    McpStreamableTestClient client = McpClients.read();
    try {
      client
          .when()
          .toolsCall(ToolGroups.RESTART_SERVICE)
          .withArguments(Map.of("service", "checkout", "idempotencyKey", "incident-read-endpoint"))
          .withErrorAssert(
              error -> assertThat(error.code()).isEqualTo(JsonRpcErrorCodes.INVALID_PARAMS))
          .send()
          .thenAssertResults();
    } finally {
      client.disconnect();
    }
  }

  @Test
  void actionEndpointWithoutApiKeyIsUnauthorized() {
    AtomicReference<ConnectFailureResponse> failure = new AtomicReference<>();

    McpClients.actionsWithoutKey().setExpectConnectFailure(failure::set).build().connect();

    assertThat(failure.get().statusCode()).isEqualTo(401);
    assertThat(failure.get().firstHeader("WWW-Authenticate"))
        .isEqualTo("ApiKey header=\"X-Api-Key\"");
  }

  @Test
  void actionEndpointWithWrongApiKeyIsUnauthorized() {
    AtomicReference<ConnectFailureResponse> failure = new AtomicReference<>();

    McpClients.actionsWithKey("wrong-key-of-sufficient-length-0000000")
        .setExpectConnectFailure(failure::set)
        .build()
        .connect();

    assertThat(failure.get().statusCode()).isEqualTo(401);
  }

  @Test
  void defaultMcpEndpointIsNotExposed() {
    given().contentType("application/json").body("{}").post("/mcp").then().statusCode(404);
  }
}
