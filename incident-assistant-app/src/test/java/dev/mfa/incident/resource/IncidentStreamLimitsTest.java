package dev.mfa.incident.resource;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.equalTo;

import dev.mfa.incident.service.IncidentStreamService;
import dev.mfa.incident.testsupport.SseTestClient;
import io.quarkus.test.common.http.TestHTTPResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import io.quarkus.test.security.TestSecurity;
import jakarta.inject.Inject;
import java.net.URI;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Heartbeat and stream limit, with a short interval and a limit of one stream. */
@QuarkusTest
@TestProfile(IncidentStreamLimitsTest.OneStream.class)
class IncidentStreamLimitsTest {

  private static final Duration TIMEOUT = Duration.ofSeconds(30);
  private static final String STREAM = "/api/v1/incidents/stream";

  public static class OneStream implements QuarkusTestProfile {
    @Override
    public Map<String, String> getConfigOverrides() {
      return Map.of(
          "incident.stream.heartbeat-interval", "200ms",
          "incident.stream.max-streams", "1",
          "incident.stream.retry-after", "9s");
    }
  }

  @TestHTTPResource(STREAM)
  URI streamUri;

  @Inject IncidentStreamService streams;

  @Test
  @TestSecurity(user = "viewer-subject", roles = "incident-viewer")
  void idleStreamGetsHeartbeatsAndSecondStreamIsRefused() throws Exception {
    try (SseTestClient open = SseTestClient.open(streamUri)) {
      assertThat(open.status(TIMEOUT)).isEqualTo(200);
      assertThat(open.nextComment(TIMEOUT)).isEqualTo("heartbeat");
      await().atMost(TIMEOUT).until(() -> streams.openStreams() == 1);

      given()
          .accept("text/event-stream")
          .when()
          .get(STREAM)
          .then()
          .statusCode(503)
          .contentType(ProblemDetail.MEDIA_TYPE)
          .header("Retry-After", "9")
          .body("status", equalTo(503));
    }

    await().atMost(TIMEOUT).until(() -> streams.openStreams() == 0);
    try (SseTestClient next = SseTestClient.open(streamUri)) {
      assertThat(next.status(TIMEOUT)).isEqualTo(200);
      assertThat(next.nextComment(TIMEOUT)).isEqualTo("heartbeat");
    }
  }
}
