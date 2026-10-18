package dev.mfa.incident.workflow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class WorkflowRecordsTest {

  private static final Instant START = Instant.parse("2026-10-18T10:00:00Z");
  private static final Instant END = START.plusMillis(1500);

  @ParameterizedTest
  @ValueSource(strings = {"BUDGET_EXCEEDED", "X", "NO_SAFE_ACTION_2"})
  void acceptsUpperSnakeCaseReasonCodes(String value) {
    assertThat(new ReasonCode(value).value()).isEqualTo(value);
  }

  @ParameterizedTest
  @ValueSource(strings = {"", "budget_exceeded", "2FAST", "HAS SPACE", "_LEADING"})
  void rejectsFreeTextReasonCodes(String value) {
    assertThatThrownBy(() -> new ReasonCode(value)).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void rejectsTooLongReasonCode() {
    String tooLong = "A".repeat(ReasonCode.MAX_LENGTH + 1);

    assertThatThrownBy(() -> new ReasonCode(tooLong)).isInstanceOf(IllegalArgumentException.class);
    assertThat(new ReasonCode("A".repeat(ReasonCode.MAX_LENGTH)).value()).hasSize(64);
  }

  @Test
  void stepDurationIsDerivedFromTimestamps() {
    StepResult result = succeeded(START, END);

    assertThat(result.duration().toMillis()).isEqualTo(1500);
  }

  @Test
  void rejectsStepThatFinishedBeforeItStarted() {
    assertThatThrownBy(() -> succeeded(END, START)).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void failedStepRequiresErrorMessage() {
    assertThatThrownBy(
            () ->
                new StepResult(
                    StepType.TRIAGE, StepStatus.FAILED, "{}", null, null, START, END, " "))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void truncatesLongErrorMessage() {
    String longMessage = "e".repeat(StepResult.MAX_ERROR_MESSAGE_LENGTH + 10);

    StepResult result =
        new StepResult(
            StepType.TRIAGE, StepStatus.FAILED, "{}", null, null, START, END, longMessage);

    assertThat(result.errorMessage()).hasSize(StepResult.MAX_ERROR_MESSAGE_LENGTH);
  }

  @Test
  void rejectsNegativeTokenCounts() {
    assertThatThrownBy(() -> new ModelUsage("model", "triage", "1", -1, 0))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void rejectsBlankModelId() {
    assertThatThrownBy(() -> new ModelUsage(" ", "triage", "1", 0, 0))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void rejectsFingerprintLongerThanColumn() {
    String fingerprint = "f".repeat(NewIncident.MAX_FINGERPRINT_LENGTH + 1);

    assertThatThrownBy(() -> new NewIncident("prometheus", fingerprint, "{}"))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void rejectsBlankSource() {
    assertThatThrownBy(() -> new NewIncident("", "fp", "{}"))
        .isInstanceOf(IllegalArgumentException.class);
  }

  private static StepResult succeeded(Instant start, Instant end) {
    return new StepResult(
        StepType.TRIAGE, StepStatus.SUCCEEDED, "{}", "{}", null, start, end, null);
  }
}
