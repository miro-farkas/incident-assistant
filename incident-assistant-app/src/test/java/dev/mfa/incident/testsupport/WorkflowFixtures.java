package dev.mfa.incident.testsupport;

import dev.mfa.incident.dto.AlertMessage;
import dev.mfa.incident.dto.ModelUsageDto;
import dev.mfa.incident.dto.NewIncidentDto;
import dev.mfa.incident.dto.StepResultDto;
import dev.mfa.incident.model.StepStatus;
import dev.mfa.incident.model.StepType;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/** Test data for workflow tests. Every incident gets a unique fingerprint. */
public final class WorkflowFixtures {

  public static final String SOURCE = "prometheus";
  public static final String ALERT_PAYLOAD =
      "{\"alertname\": \"HighErrorRate\", \"service\": \"checkout\", \"value\": 0.27}";
  public static final ModelUsageDto MODEL_USAGE =
      new ModelUsageDto("llama3.2:3b", "triage", "3", 812, 96);
  public static final long STEP_DURATION_MS = 2350;

  public static final Instant ALERT_STARTS_AT = Instant.parse("2026-10-18T09:58:00Z");

  private static final Instant STARTED_AT = Instant.parse("2026-10-18T10:00:00Z");
  private static final Instant FINISHED_AT = STARTED_AT.plusMillis(STEP_DURATION_MS);

  private WorkflowFixtures() {}

  public static NewIncidentDto newIncident() {
    return new NewIncidentDto(SOURCE, uniqueFingerprint(), ALERT_PAYLOAD);
  }

  public static String uniqueFingerprint() {
    return "fp-" + UUID.randomUUID();
  }

  /** A valid alert with every property set and a unique fingerprint. */
  public static AlertMessage alert() {
    return alert(uniqueFingerprint());
  }

  public static AlertMessage alert(String fingerprint) {
    return new AlertMessage(
        SOURCE,
        fingerprint,
        "HighErrorRate",
        "Error rate above 5% for checkout",
        ALERT_STARTS_AT,
        "critical",
        "checkout",
        "Ratio of 5xx responses was 0.27 over the last 5 minutes.",
        Map.of("env", "prod", "region", "eu-west-1"),
        "https://prometheus.example.com/graph?g0.expr=http_errors");
  }

  /** Output of a successful triage step (A-0014). */
  public static final String TRIAGE_OUTPUT =
      "{\"severity\": \"HIGH\", \"category\": \"DEPLOYMENT\", \"affectedService\":"
          + " \"checkout\", \"summary\": \"Error rate of checkout rose after a release.\"}";

  /** Output of a successful diagnosis step (A-0015). */
  public static final String DIAGNOSIS_OUTPUT =
      "{\"hypothesis\": \"Release 2026.10.18-1 of checkout broke the payment call.\","
          + " \"confidence\": \"HIGH\", \"evidence\": [{\"statement\": \"Errors started with"
          + " the deployment.\", \"sourceType\": \"TOOL\", \"source\":"
          + " \"get_recent_deployments\"}]}";

  public static StepResultDto succeeded(StepType type) {
    return new StepResultDto(
        type,
        StepStatus.SUCCEEDED,
        "{\"step\": \"" + type + "\"}",
        switch (type) {
          case TRIAGE -> TRIAGE_OUTPUT;
          case DIAGNOSIS -> DIAGNOSIS_OUTPUT;
          default -> "{\"result\": \"ok\"}";
        },
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
