package dev.mfa.incident.opstools.tool;

import static org.assertj.core.api.Assertions.assertThat;

import dev.mfa.incident.opstools.testsupport.McpClients;
import io.quarkiverse.mcp.server.ToolResponse;
import io.quarkiverse.mcp.server.test.McpAssured.McpStreamableTestClient;
import io.quarkus.test.junit.QuarkusTest;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.IntStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Read tools against the default scenarios and the fixed test anchor 2026-10-18T10:00:00Z. */
@QuarkusTest
class ReadToolsTest {

  private static final String ANCHOR = "2026-10-18T10:00:00Z";

  private McpStreamableTestClient client;

  @BeforeEach
  void connect() {
    client = McpClients.read();
  }

  @AfterEach
  void disconnect() {
    client.disconnect();
  }

  @Test
  void metricsCoverTheWindowOldestFirst() {
    JsonObject metrics = success(ToolGroups.GET_SERVICE_METRICS, Map.of("service", "checkout"));

    JsonArray points = metrics.getJsonArray("points");
    assertThat(points).hasSize(12);
    assertThat(points.getJsonObject(0).getString("timestamp")).isEqualTo("2026-10-18T09:05:00Z");
    assertThat(points.getJsonObject(11).getString("timestamp")).isEqualTo(ANCHOR);
    assertThat(metrics.getString("from")).isEqualTo("2026-10-18T09:05:00Z");
    assertThat(metrics.getString("to")).isEqualTo(ANCHOR);
    assertThat(metrics.getInteger("stepMinutes")).isEqualTo(5);
    assertThat(metrics.getBoolean("truncated")).isFalse();
  }

  @Test
  void metricsShowTheBadDeployment() {
    JsonArray points =
        success(ToolGroups.GET_SERVICE_METRICS, Map.of("service", "checkout"))
            .getJsonArray("points");

    JsonObject beforeDeployment = points.getJsonObject(0);
    JsonObject afterDeployment = points.getJsonObject(11);
    assertThat(beforeDeployment.getDouble("errorRatePercent")).isLessThan(1);
    assertThat(afterDeployment.getDouble("errorRatePercent")).isGreaterThan(10);
    assertThat(afterDeployment.getLong("latencyP99Ms")).isGreaterThan(800);
  }

  @Test
  void metricsShowTheMemoryLeak() {
    JsonArray points =
        success(
                ToolGroups.GET_SERVICE_METRICS,
                Map.of("service", "inventory", "windowMinutes", 180, "stepMinutes", 30))
            .getJsonArray("points");

    assertThat(points.getJsonObject(0).getDouble("memoryPercent")).isLessThan(65);
    assertThat(points.getJsonObject(points.size() - 2).getDouble("memoryPercent"))
        .isGreaterThan(90);
  }

  @Test
  void metricsShowTheDownstreamTimeout() {
    JsonArray points =
        success(ToolGroups.GET_SERVICE_METRICS, Map.of("service", "payments"))
            .getJsonArray("points");

    assertThat(points.getJsonObject(0).getLong("latencyP99Ms")).isLessThan(300);
    assertThat(points.getJsonObject(11).getLong("latencyP99Ms")).isEqualTo(5000);
  }

  @Test
  void metricsKeepTheNewestPointsOfALongWindow() {
    JsonObject metrics =
        success(
            ToolGroups.GET_SERVICE_METRICS,
            Map.of("service", "checkout", "windowMinutes", 1440, "stepMinutes", 1));

    JsonArray points = metrics.getJsonArray("points");
    assertThat(points).hasSize(120);
    assertThat(points.getJsonObject(0).getString("timestamp")).isEqualTo("2026-10-18T08:01:00Z");
    assertThat(points.getJsonObject(119).getString("timestamp")).isEqualTo(ANCHOR);
    assertThat(metrics.getBoolean("truncated")).isTrue();
  }

  @Test
  void metricsRejectAWindowOutOfRange() {
    assertThat(
            error(
                ToolGroups.GET_SERVICE_METRICS, Map.of("service", "checkout", "windowMinutes", 2)))
        .contains("windowMinutes");
    assertThat(
            error(
                ToolGroups.GET_SERVICE_METRICS,
                Map.of("service", "checkout", "windowMinutes", 1441)))
        .contains("windowMinutes");
    assertThat(
            error(ToolGroups.GET_SERVICE_METRICS, Map.of("service", "checkout", "stepMinutes", 0)))
        .contains("stepMinutes");
  }

  @Test
  void logsFindTheErrorsOfTheBadDeploymentNewestFirst() {
    JsonObject result =
        success(
            ToolGroups.SEARCH_LOGS,
            Map.of("service", "checkout", "minLevel", "ERROR", "limit", 100));

    JsonArray entries = result.getJsonArray("entries");
    // An HTTP 500 every minute since the deployment 25 minutes ago, a stack trace every second.
    assertThat(result.getInteger("totalMatches")).isEqualTo(25 + 13);
    assertThat(entries).hasSize(38);
    assertThat(result.getBoolean("truncated")).isFalse();
    List<String> timestamps =
        IntStream.range(0, entries.size())
            .mapToObj(i -> entries.getJsonObject(i).getString("timestamp"))
            .toList();
    assertThat(timestamps).isSortedAccordingTo((a, b) -> b.compareTo(a));
    assertThat(entries.getJsonObject(0).getString("message")).contains("HTTP 500", "2.16.0");
  }

  @Test
  void logsCutLongMessages() {
    JsonObject result =
        success(
            ToolGroups.SEARCH_LOGS,
            Map.of("service", "checkout", "query", "nullpointerexception", "limit", 1));

    JsonObject entry = result.getJsonArray("entries").getJsonObject(0);
    assertThat(entry.getString("message")).hasSize(500).startsWith("java.lang.NullPointer");
    assertThat(entry.getBoolean("messageTruncated")).isTrue();
    assertThat(result.getInteger("totalMatches")).isEqualTo(13);
    assertThat(result.getBoolean("truncated")).isTrue();
  }

  @Test
  void logsReturnAtMostTheLimit() {
    JsonObject result =
        success(ToolGroups.SEARCH_LOGS, Map.of("service", "checkout", "minLevel", "ERROR"));

    assertThat(result.getJsonArray("entries")).hasSize(20);
    assertThat(result.getInteger("totalMatches")).isEqualTo(38);
    assertThat(result.getBoolean("truncated")).isTrue();
  }

  @Test
  void logsShowTheOutOfMemoryKills() {
    JsonObject result =
        success(
            ToolGroups.SEARCH_LOGS, Map.of("service", "inventory", "query", "OutOfMemoryError"));

    assertThat(result.getInteger("totalMatches")).isEqualTo(3);
    assertThat(result.getJsonArray("entries").getJsonObject(0).getString("pod"))
        .isEqualTo("inventory-1");
  }

  @Test
  void logsShowTheTimedOutDependency() {
    JsonObject result =
        success(
            ToolGroups.SEARCH_LOGS,
            Map.of("service", "payments", "query", "timed out", "sinceMinutes", 30));

    assertThat(result.getInteger("totalMatches")).isEqualTo(15);
    assertThat(result.getJsonArray("entries").getJsonObject(0).getString("message"))
        .isEqualTo("Call to fraud-check timed out after 5000 ms");
  }

  @Test
  void logsRejectInvalidArguments() {
    assertThat(error(ToolGroups.SEARCH_LOGS, Map.of("service", "checkout", "limit", 101)))
        .contains("limit");
    assertThat(error(ToolGroups.SEARCH_LOGS, Map.of("service", "checkout", "sinceMinutes", 0)))
        .contains("sinceMinutes");
    assertThat(
            error(ToolGroups.SEARCH_LOGS, Map.of("service", "checkout", "query", "x".repeat(201))))
        .contains("query");
  }

  @Test
  void deploymentsAreNewestFirstAndLimited() {
    JsonObject history =
        success(ToolGroups.GET_RECENT_DEPLOYMENTS, Map.of("service", "checkout", "limit", 2));

    JsonArray deployments = history.getJsonArray("deployments");
    assertThat(deployments).hasSize(2);
    assertThat(history.getBoolean("truncated")).isTrue();
    JsonObject running = deployments.getJsonObject(0);
    assertThat(running.getString("version")).isEqualTo("2.16.0");
    assertThat(running.getString("deployedAt")).isEqualTo("2026-10-18T09:35:00Z");
    assertThat(running.getString("id")).isEqualTo("deploy-checkout-2.16.0");
    assertThat(deployments.getJsonObject(1).getString("version")).isEqualTo("2.15.0");
  }

  @Test
  void deploymentsWithoutRecentChangeForOtherScenarios() {
    JsonObject history = success(ToolGroups.GET_RECENT_DEPLOYMENTS, Map.of("service", "inventory"));

    assertThat(history.getJsonArray("deployments")).hasSize(5);
    assertThat(history.getBoolean("truncated")).isFalse();
    assertThat(history.getJsonArray("deployments").getJsonObject(0).getString("deployedAt"))
        .isEqualTo("2026-10-12T10:00:00Z");
  }

  @Test
  void deploymentsRejectALimitOutOfRange() {
    assertThat(error(ToolGroups.GET_RECENT_DEPLOYMENTS, Map.of("service", "checkout", "limit", 21)))
        .contains("limit");
  }

  @Test
  void healthShowsTheFailingDependency() {
    JsonObject health = success(ToolGroups.GET_SERVICE_HEALTH, Map.of("service", "payments"));

    assertThat(health.getString("status")).isEqualTo("DEGRADED");
    assertThat(health.getString("version")).isEqualTo("5.4.1");
    JsonObject fraudCheck = health.getJsonArray("dependencies").getJsonObject(0);
    assertThat(fraudCheck.getString("name")).isEqualTo("fraud-check");
    assertThat(fraudCheck.getString("status")).isEqualTo("DOWN");
    assertThat(health.getJsonArray("dependencies").getJsonObject(1).getString("status"))
        .isEqualTo("UP");
  }

  @Test
  void healthShowsRestartedPods() {
    JsonObject health = success(ToolGroups.GET_SERVICE_HEALTH, Map.of("service", "inventory"));

    assertThat(health.getInteger("replicasDesired")).isEqualTo(3);
    assertThat(health.getInteger("replicasReady")).isEqualTo(2);
    assertThat(health.getInteger("restartsLastHour")).isEqualTo(3);
    assertThat(health.getString("checkedAt")).isEqualTo(ANCHOR);
  }

  @Test
  void unknownServiceIsAToolError() {
    assertThat(error(ToolGroups.GET_SERVICE_HEALTH, Map.of("service", "shipping")))
        .isEqualTo("Unknown service 'shipping'. Known services: checkout, payments, inventory");
  }

  @Test
  void blankServiceIsAToolError() {
    assertThat(error(ToolGroups.GET_SERVICE_HEALTH, Map.of("service", " "))).contains("service");
  }

  @Test
  void resultIsAlsoSentAsText() {
    ToolResponse response = call(ToolGroups.GET_SERVICE_HEALTH, Map.of("service", "checkout"));

    assertThat(new JsonObject(response.firstContent().asText().text()))
        .isEqualTo(response.structuredContent());
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
