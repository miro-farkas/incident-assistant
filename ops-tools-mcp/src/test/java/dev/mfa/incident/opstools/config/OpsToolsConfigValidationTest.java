package dev.mfa.incident.opstools.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.mfa.incident.opstools.testsupport.TestConfig;
import io.smallrye.config.ConfigValidationException;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.Test;

class OpsToolsConfigValidationTest {

  private static final String API_KEY = "ops.actions.api-key";

  @Test
  void defaultsApply() {
    OpsToolsConfig config = TestConfig.with(Map.of());

    assertThat(config.services()).isEmpty();
    assertThat(config.services().get("checkout").scenario()).isEqualTo(Scenario.HEALTHY);
    assertThat(config.services().get("checkout").fault()).isEqualTo(Fault.NONE);
    assertThat(config.fakeData().anchor()).isEmpty();
    assertThat(config.faults().slowDelay()).isEqualTo(Duration.ofSeconds(5));
    assertThat(config.actions().idempotencyRetention()).isEqualTo(Duration.ofHours(24));
    assertThat(config.actions().maxIdempotencyRecords()).isEqualTo(10_000);
  }

  @Test
  void scenarioAndFaultAreReadInKebabCase() {
    OpsToolsConfig config =
        TestConfig.with(
            Map.of(
                "ops.services.payments.scenario", "downstream-timeout",
                "ops.services.payments.fault", "unavailable"));

    assertThat(config.services().get("payments").scenario()).isEqualTo(Scenario.DOWNSTREAM_TIMEOUT);
    assertThat(config.services().get("payments").fault()).isEqualTo(Fault.UNAVAILABLE);
  }

  @Test
  void rejectsMissingApiKey() {
    assertThatThrownBy(() -> TestConfig.build(Map.of()))
        .isInstanceOf(ConfigValidationException.class)
        .hasMessageContaining(API_KEY);
  }

  @Test
  void rejectsShortApiKey() {
    assertThatThrownBy(() -> TestConfig.with(Map.of(API_KEY, "a".repeat(31))))
        .isInstanceOf(ConfigValidationException.class)
        .hasMessageContaining(API_KEY);
  }

  @Test
  void rejectsUnknownScenario() {
    assertThatThrownBy(() -> TestConfig.with(Map.of("ops.services.checkout.scenario", "on-fire")))
        .hasMessageContaining("ops.services.checkout.scenario");
  }

  @Test
  void rejectsSlowDelayAboveFiveMinutes() {
    assertThatThrownBy(() -> TestConfig.with(Map.of("ops.faults.slow-delay", "6m")))
        .isInstanceOf(ConfigValidationException.class)
        .hasMessageContaining("ops.faults.slow-delay");
  }

  @Test
  void rejectsZeroIdempotencyRecords() {
    assertThatThrownBy(() -> TestConfig.with(Map.of("ops.actions.max-idempotency-records", "0")))
        .isInstanceOf(ConfigValidationException.class)
        .hasMessageContaining("ops.actions.max-idempotency-records");
  }
}
