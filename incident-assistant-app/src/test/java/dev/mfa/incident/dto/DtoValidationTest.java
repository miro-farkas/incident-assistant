package dev.mfa.incident.dto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.mfa.incident.model.StepStatus;
import dev.mfa.incident.model.StepType;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class DtoValidationTest {

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
    assertThat(new ReasonCode("A".repeat(ReasonCode.MAX_LENGTH)).value())
        .hasSize(ReasonCode.MAX_LENGTH);
  }

  @Test
  void stepDurationIsDerivedFromTimestamps() {
    StepResultDto result = succeeded(START, END);

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
                new StepResultDto(
                    StepType.TRIAGE, StepStatus.FAILED, "{}", null, null, START, END, " "))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void truncatesLongErrorMessage() {
    String longMessage = "e".repeat(StepResultDto.MAX_ERROR_MESSAGE_LENGTH + 10);

    StepResultDto result =
        new StepResultDto(
            StepType.TRIAGE, StepStatus.FAILED, "{}", null, null, START, END, longMessage);

    assertThat(result.errorMessage()).hasSize(StepResultDto.MAX_ERROR_MESSAGE_LENGTH);
  }

  @Test
  void rejectsNegativeTokenCounts() {
    assertThatThrownBy(() -> new ModelUsageDto("model", "triage", "1", -1, 0))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void rejectsBlankModelId() {
    assertThatThrownBy(() -> new ModelUsageDto(" ", "triage", "1", 0, 0))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void rejectsFingerprintLongerThanColumn() {
    String fingerprint = "f".repeat(NewIncidentDto.MAX_FINGERPRINT_LENGTH + 1);

    assertThatThrownBy(() -> new NewIncidentDto("prometheus", fingerprint, "{}"))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void rejectsBlankSource() {
    assertThatThrownBy(() -> new NewIncidentDto("", "fp", "{}"))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void rejectsBlankAlertPayload() {
    assertThatThrownBy(() -> new NewIncidentDto("prometheus", "fp", " "))
        .isInstanceOf(IllegalArgumentException.class);
  }

  private static StepResultDto succeeded(Instant start, Instant end) {
    return new StepResultDto(
        StepType.TRIAGE, StepStatus.SUCCEEDED, "{}", "{}", null, start, end, null);
  }
}
