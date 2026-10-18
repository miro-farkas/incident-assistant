package dev.mfa.incident.workflow;

import java.time.Instant;
import java.util.UUID;

/** Test data for workflow tests. Every incident gets a unique fingerprint. */
public final class WorkflowFixtures {

  public static final String SOURCE = "prometheus";
  public static final String ALERT_PAYLOAD =
      "{\"alertname\": \"HighErrorRate\", \"service\": \"checkout\", \"value\": 0.27}";
  public static final ModelUsage MODEL_USAGE =
      new ModelUsage("llama3.2:3b", "triage", "3", 812, 96);

  private static final Instant STARTED_AT = Instant.parse("2026-10-18T10:00:00Z");
  private static final Instant FINISHED_AT = STARTED_AT.plusMillis(2350);

  private WorkflowFixtures() {}

  public static NewIncident newIncident() {
    return new NewIncident(SOURCE, "fp-" + UUID.randomUUID(), ALERT_PAYLOAD);
  }

  public static StepResult succeeded(StepType type) {
    return new StepResult(
        type,
        StepStatus.SUCCEEDED,
        "{\"step\": \"" + type + "\"}",
        "{\"result\": \"ok\"}",
        MODEL_USAGE,
        STARTED_AT,
        FINISHED_AT,
        null);
  }

  public static StepResult failed(StepType type, String errorMessage) {
    return new StepResult(
        type,
        StepStatus.FAILED,
        "{\"step\": \"" + type + "\"}",
        null,
        null,
        STARTED_AT,
        FINISHED_AT,
        errorMessage);
  }
}
