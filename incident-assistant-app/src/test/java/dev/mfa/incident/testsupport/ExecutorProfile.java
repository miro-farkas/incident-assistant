package dev.mfa.incident.testsupport;

import io.quarkus.test.junit.QuarkusTestProfile;
import java.util.Map;

/**
 * The step executor runs, quickly, with the default concurrency of two; the AI services are mocked
 * by the tests (P-0010).
 */
public class ExecutorProfile implements QuarkusTestProfile {

  @Override
  public Map<String, String> getConfigOverrides() {
    return Map.of(
        "incident.workflow.executor.enabled", "true",
        "incident.workflow.executor.interval", "100ms");
  }
}
