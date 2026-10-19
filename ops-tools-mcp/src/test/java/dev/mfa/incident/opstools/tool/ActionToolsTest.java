package dev.mfa.incident.opstools.tool;

import static org.assertj.core.api.Assertions.assertThat;

import dev.mfa.incident.opstools.testsupport.McpClients;
import io.quarkiverse.mcp.server.ToolResponse;
import io.quarkiverse.mcp.server.test.McpAssured.McpStreamableTestClient;
import io.quarkus.test.junit.QuarkusTest;
import io.vertx.core.json.JsonObject;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Action tools; every test uses fresh idempotency keys because the store outlives a test. */
@QuarkusTest
class ActionToolsTest {

  private McpStreamableTestClient client;

  @BeforeEach
  void connect() {
    client = McpClients.actions();
  }

  @AfterEach
  void disconnect() {
    client.disconnect();
  }

  @Test
  void restartCompletes() {
    String key = newKey();

    JsonObject result = success(ToolGroups.RESTART_SERVICE, args("checkout", key));

    assertThat(result.getString("status")).isEqualTo("COMPLETED");
    assertThat(result.getString("action")).isEqualTo("RESTART_SERVICE");
    assertThat(result.getString("service")).isEqualTo("checkout");
    assertThat(result.getString("message"))
        .isEqualTo("Restarted all 3 pods of checkout one at a time");
    assertThat(result.getString("idempotencyKey")).isEqualTo(key);
    assertThat(result.getString("actionId"))
        .isEqualTo(UUID.nameUUIDFromBytes(key.getBytes(StandardCharsets.UTF_8)).toString());
    assertThat(result.getBoolean("replayed")).isFalse();
  }

  @Test
  void repeatedKeyReturnsTheOriginalResult() {
    String key = newKey();
    JsonObject first = success(ToolGroups.RESTART_SERVICE, args("payments", key));

    JsonObject second = success(ToolGroups.RESTART_SERVICE, args("payments", key));

    assertThat(second.getBoolean("replayed")).isTrue();
    assertThat(second.copy().put("replayed", false)).isEqualTo(first);
  }

  @Test
  void repeatedKeyWithOtherParametersIsRefused() {
    String key = newKey();
    success(ToolGroups.SCALE_SERVICE, args("checkout", key, "replicas", 5));

    assertThat(error(ToolGroups.SCALE_SERVICE, args("checkout", key, "replicas", 6)))
        .isEqualTo(
            "Idempotency key '%s' was already used for a different action or different parameters."
                .formatted(key));
    assertThat(error(ToolGroups.SCALE_SERVICE, args("inventory", key, "replicas", 5)))
        .contains("already used");
    assertThat(error(ToolGroups.RESTART_SERVICE, args("checkout", key))).contains("already used");
  }

  @Test
  void rollbackDefaultsToThePreviousRelease() {
    JsonObject result = success(ToolGroups.ROLLBACK_DEPLOYMENT, args("checkout", newKey()));

    assertThat(result.getString("status")).isEqualTo("COMPLETED");
    assertThat(result.getString("message")).isEqualTo("Rolled back checkout from 2.16.0 to 2.15.0");
  }

  @Test
  void rollbackToAnEarlierRelease() {
    JsonObject result =
        success(
            ToolGroups.ROLLBACK_DEPLOYMENT, args("checkout", newKey(), "targetVersion", "2.14.0"));

    assertThat(result.getString("message")).isEqualTo("Rolled back checkout from 2.16.0 to 2.14.0");
  }

  @Test
  void rollbackToAnUnknownReleaseIsRejectedAndReplayed() {
    String key = newKey();
    Map<String, Object> arguments = args("checkout", key, "targetVersion", "2.16.0");

    JsonObject rejected = success(ToolGroups.ROLLBACK_DEPLOYMENT, arguments);
    JsonObject replayed = success(ToolGroups.ROLLBACK_DEPLOYMENT, arguments);

    assertThat(rejected.getString("status")).isEqualTo("REJECTED");
    assertThat(rejected.getString("message"))
        .isEqualTo(
            "Version 2.16.0 is not an earlier release of checkout; earlier releases: 2.13.0,"
                + " 2.14.0, 2.14.1, 2.15.0");
    assertThat(replayed.getString("status")).isEqualTo("REJECTED");
    assertThat(replayed.getBoolean("replayed")).isTrue();
  }

  @Test
  void rollbackRejectsAMalformedVersion() {
    assertThat(
            error(
                ToolGroups.ROLLBACK_DEPLOYMENT,
                args("checkout", newKey(), "targetVersion", "../2.15.0")))
        .contains("targetVersion");
  }

  @Test
  void scaleCompletes() {
    JsonObject result =
        success(ToolGroups.SCALE_SERVICE, args("checkout", newKey(), "replicas", 5));

    assertThat(result.getString("status")).isEqualTo("COMPLETED");
    assertThat(result.getString("message")).isEqualTo("Scaled checkout from 3 to 5 replicas");
  }

  @Test
  void scaleAboveTheServiceMaximumIsRejected() {
    JsonObject result =
        success(ToolGroups.SCALE_SERVICE, args("inventory", newKey(), "replicas", 9));

    assertThat(result.getString("status")).isEqualTo("REJECTED");
    assertThat(result.getString("message")).isEqualTo("inventory cannot run more than 8 replicas");
  }

  @Test
  void scaleRejectsReplicasOutOfRange() {
    assertThat(error(ToolGroups.SCALE_SERVICE, args("checkout", newKey(), "replicas", 0)))
        .contains("replicas");
    assertThat(error(ToolGroups.SCALE_SERVICE, args("checkout", newKey(), "replicas", 51)))
        .contains("replicas");
  }

  @Test
  void idempotencyKeyIsRequired() {
    assertThat(error(ToolGroups.RESTART_SERVICE, Map.of("service", "checkout")))
        .contains("idempotencyKey");
  }

  @Test
  void malformedIdempotencyKeyIsRefused() {
    assertThat(error(ToolGroups.RESTART_SERVICE, args("checkout", "short")))
        .contains("idempotencyKey");
    assertThat(error(ToolGroups.RESTART_SERVICE, args("checkout", "key with spaces 123")))
        .contains("idempotencyKey");
  }

  @Test
  void unknownServiceIsRefusedWithoutUsingTheKey() {
    String key = newKey();

    assertThat(error(ToolGroups.RESTART_SERVICE, args("shipping", key)))
        .startsWith("Unknown service 'shipping'");

    JsonObject result = success(ToolGroups.RESTART_SERVICE, args("checkout", key));
    assertThat(result.getBoolean("replayed")).isFalse();
  }

  private static Map<String, Object> args(String service, String key, Object... more) {
    Map<String, Object> arguments = new HashMap<>();
    arguments.put("service", service);
    arguments.put("idempotencyKey", key);
    for (int i = 0; i < more.length; i += 2) {
      arguments.put((String) more[i], more[i + 1]);
    }
    return arguments;
  }

  private static String newKey() {
    return "incident-" + UUID.randomUUID();
  }

  private JsonObject success(String tool, Map<String, Object> arguments) {
    ToolResponse response = call(tool, arguments);
    assertThat(response.isError()).as("tool error: %s", response.content()).isFalse();
    return (JsonObject) response.structuredContent();
  }

  private String error(String tool, Map<String, Object> arguments) {
    ToolResponse response = call(tool, arguments);
    assertThat(response.isError()).isTrue();
    return response.firstContent().asText().text();
  }

  private ToolResponse call(String tool, Map<String, Object> arguments) {
    AtomicReference<ToolResponse> response = new AtomicReference<>();
    client.when().toolsCall(tool, arguments, response::set).thenAssertResults();
    return response.get();
  }
}
