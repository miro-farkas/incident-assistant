package dev.mfa.incident.opstools.testsupport;

import io.quarkiverse.mcp.server.test.McpAssured;
import io.quarkiverse.mcp.server.test.McpAssured.McpStreamableTestClient;
import io.vertx.core.MultiMap;

/** Streamable HTTP MCP clients for the two endpoints of the server under test. */
public final class McpClients {

  public static final String READ_PATH = "/mcp/read";
  public static final String ACTIONS_PATH = "/mcp/actions";
  public static final String API_KEY_HEADER = "X-Api-Key";

  /** Value of {@code %test.ops.actions.api-key}. */
  public static final String TEST_API_KEY = "test-only-actions-key-not-a-secret-01";

  private McpClients() {}

  public static McpStreamableTestClient read() {
    return McpAssured.newStreamableClient().setMcpPath(READ_PATH).build().connect();
  }

  public static McpStreamableTestClient actions() {
    return actionsWithKey(TEST_API_KEY).build().connect();
  }

  public static McpStreamableTestClient.Builder actionsWithoutKey() {
    return McpAssured.newStreamableClient().setMcpPath(ACTIONS_PATH);
  }

  public static McpStreamableTestClient.Builder actionsWithKey(String apiKey) {
    return actionsWithoutKey()
        .setAdditionalHeaders(
            message -> MultiMap.caseInsensitiveMultiMap().add(API_KEY_HEADER, apiKey));
  }
}
