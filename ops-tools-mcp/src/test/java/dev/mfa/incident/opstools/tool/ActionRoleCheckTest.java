package dev.mfa.incident.opstools.tool;

import static org.assertj.core.api.Assertions.assertThat;

import dev.mfa.incident.opstools.testsupport.McpClients;
import io.quarkiverse.mcp.server.JsonRpcErrorCodes;
import io.quarkiverse.mcp.server.test.McpAssured.McpStreamableTestClient;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * The role check on the action tools holds on its own: with the HTTP path policy switched off, a
 * client without the API key reaches the endpoint but cannot call an action (defence in depth,
 * P-0006).
 */
@QuarkusTest
@TestProfile(ActionRoleCheckTest.WithoutPathPolicy.class)
class ActionRoleCheckTest {

  public static class WithoutPathPolicy implements QuarkusTestProfile {
    @Override
    public Map<String, String> getConfigOverrides() {
      return Map.of("quarkus.http.auth.permission.ops-actions.enabled", "false");
    }
  }

  @Test
  void actionWithoutRoleIsASecurityError() {
    McpStreamableTestClient client = McpClients.actionsWithoutKey().build().connect();
    try {
      client
          .when()
          .toolsCall(ToolGroups.RESTART_SERVICE)
          .withArguments(Map.of("service", "checkout", "idempotencyKey", "incident-no-role"))
          .withErrorAssert(
              error -> assertThat(error.code()).isEqualTo(JsonRpcErrorCodes.SECURITY_ERROR))
          .send()
          .thenAssertResults();
    } finally {
      client.disconnect();
    }
  }
}
