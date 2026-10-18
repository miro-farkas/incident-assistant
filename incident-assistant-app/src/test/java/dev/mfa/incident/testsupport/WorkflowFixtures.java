package dev.mfa.incident.testsupport;

import dev.mfa.incident.dto.ModelUsageDto;
import dev.mfa.incident.dto.NewIncidentDto;
import dev.mfa.incident.dto.StepResultDto;
import dev.mfa.incident.model.StepStatus;
import dev.mfa.incident.model.StepType;
import java.time.Instant;
import java.util.UUID;

/** Test data for workflow tests. Every incident gets a unique fingerprint. */
public final class WorkflowFixtures {

  public static final String SOURCE = "prometheus";
  public static final String ALERT_PAYLOAD =
      "{\"alertname\": \"HighErrorRate\", \"service\": \"checkout\", \"value\": 0.27}";
  public static final ModelUsageDto MODEL_USAGE =
      new ModelUsageDto("llama3.2:3b", "triage", "3", 812, 96);
  public static final long STEP_DURATION_MS = 2350;

  private static final Instant STARTED_AT = Instant.parse("2026-10-18T10:00:00Z");
  private static final Instant FINISHED_AT = STARTED_AT.plusMillis(STEP_DURATION_MS);

  private WorkflowFixtures() {}

  public static NewIncidentDto newIncident() {
    return new NewIncidentDto(SOURCE, "fp-" + UUID.randomUUID(), ALERT_PAYLOAD);
  }

  public static StepResultDto succeeded(StepType type) {
    return new StepResultDto(
        type,
        StepStatus.SUCCEEDED,
        "{\"step\": \"" + type + "\"}",
        "{\"result\": \"ok\"}",
        MODEL_USAGE,
        STARTED_AT,
        FINISHED_AT,
        null);
  }

  public static StepResultDto failed(StepType type, String errorMessage) {
    return new StepResultDto(
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
