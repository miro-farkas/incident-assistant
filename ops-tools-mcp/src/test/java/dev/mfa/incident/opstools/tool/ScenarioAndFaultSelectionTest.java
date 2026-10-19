package dev.mfa.incident.opstools.tool;

import static org.assertj.core.api.Assertions.assertThat;

import dev.mfa.incident.opstools.testsupport.McpClients;
import io.quarkiverse.mcp.server.ToolResponse;
import io.quarkiverse.mcp.server.test.McpAssured.McpStreamableTestClient;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import io.vertx.core.json.JsonObject;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Scenario and fault are chosen per service by configuration. */
@QuarkusTest
@TestProfile(ScenarioAndFaultSelectionTest.Profile.class)
class ScenarioAndFaultSelectionTest {

  private static final Duration SLOW_DELAY = Duration.ofMillis(300);

  public static class Profile implements QuarkusTestProfile {
    @Override
    public Map<String, String> getConfigOverrides() {
      return Map.of(
          "ops.services.checkout.scenario", "healthy",
          "ops.services.payments.fault", "unavailable",
          "ops.services.inventory.scenario", "bad-deployment",
          "ops.services.inventory.fault", "slow",
          "ops.faults.slow-delay", SLOW_DELAY.toMillis() + "ms");
    }
  }

  private McpStreamableTestClient read;

  @BeforeEach
  void connect() {
    read = McpClients.read();
  }

  @AfterEach
  void disconnect() {
    read.disconnect();
  }

  @Test
  void healthyScenarioHasNoErrorsAndNoRecentDeployment() {
    JsonObject health =
        structured(call(read, ToolGroups.GET_SERVICE_HEALTH, Map.of("service", "checkout")));
    JsonObject errors =
        structured(
            call(
                read,
                ToolGroups.SEARCH_LOGS,
                Map.of("service", "checkout", "minLevel", "WARN", "sinceMinutes", 1440)));
    JsonObject deployments =
        structured(
            call(
                read,
                ToolGroups.GET_RECENT_DEPLOYMENTS,
                Map.of("service", "checkout", "limit", 1)));

    assertThat(health.getString("status")).isEqualTo("UP");
    assertThat(errors.getInteger("totalMatches")).isZero();
    assertThat(deployments.getJsonArray("deployments").getJsonObject(0).getString("deployedAt"))
        .isEqualTo("2026-10-16T10:00:00Z");
  }

  @Test
  void anyServiceCanSimulateAnyScenario() {
    JsonObject deployments =
        structured(
            call(
                read,
                ToolGroups.GET_RECENT_DEPLOYMENTS,
                Map.of("service", "inventory", "limit", 1)));

    JsonObject running = deployments.getJsonArray("deployments").getJsonObject(0);
    assertThat(running.getString("version")).isEqualTo("1.8.2");
    assertThat(running.getString("deployedAt")).isEqualTo("2026-10-18T09:35:00Z");
  }

  @Test
  void sameConfigurationGivesTheSameData() {
    Map<String, Object> arguments = Map.of("service", "inventory", "windowMinutes", 240);

    assertThat(structured(call(read, ToolGroups.GET_SERVICE_METRICS, arguments)))
        .isEqualTo(structured(call(read, ToolGroups.GET_SERVICE_METRICS, arguments)));
  }

  @Test
  void unavailableBackendIsAToolErrorForReadTools() {
    ToolResponse response =
        call(read, ToolGroups.GET_SERVICE_HEALTH, Map.of("service", "payments"));

    assertThat(response.isError()).isTrue();
    assertThat(response.firstContent().asText().text())
        .isEqualTo("The operations backend for service 'payments' is unavailable; retry later.");
  }

  @Test
  void unavailableBackendExecutesNoActionAndKeepsTheKeyUsable() {
    McpStreamableTestClient actions = McpClients.actions();
    try {
      String key = "incident-" + UUID.randomUUID();

      ToolResponse refused =
          call(
              actions,
              ToolGroups.RESTART_SERVICE,
              Map.of("service", "payments", "idempotencyKey", key));
      ToolResponse other =
          call(
              actions,
              ToolGroups.RESTART_SERVICE,
              Map.of("service", "checkout", "idempotencyKey", key));

      assertThat(refused.isError()).isTrue();
      assertThat(refused.firstContent().asText().text()).contains("unavailable");
      assertThat(structured(other).getBoolean("replayed")).isFalse();
    } finally {
      actions.disconnect();
    }
  }

  @Test
  void slowBackendDelaysTheAnswer() {
    long start = System.nanoTime();

    ToolResponse response =
        call(read, ToolGroups.GET_SERVICE_HEALTH, Map.of("service", "inventory"));

    assertThat(response.isError()).isFalse();
    assertThat(Duration.ofNanos(System.nanoTime() - start)).isGreaterThanOrEqualTo(SLOW_DELAY);
  }

  private static JsonObject structured(ToolResponse response) {
    assertThat(response.isError()).as("tool error: %s", response.content()).isFalse();
    return (JsonObject) response.structuredContent();
  }

  private static ToolResponse call(
      McpStreamableTestClient client, String tool, Map<String, Object> arguments) {
    AtomicReference<ToolResponse> response = new AtomicReference<>();
    client.when().toolsCall(tool, arguments, response::set).thenAssertResults();
    return response.get();
  }
}
