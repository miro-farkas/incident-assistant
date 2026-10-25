package dev.mfa.incident.ai;

import static org.assertj.core.api.Assertions.assertThat;

import io.quarkus.arc.Arc;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import io.smallrye.faulttolerance.FaultToleranceOperationProvider;
import io.smallrye.faulttolerance.config.FaultToleranceOperation;
import jakarta.inject.Inject;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * The fault-tolerance configuration of {@link TriageAssistant} in {@code application.properties}
 * takes effect. Its keys name the generated AI service class; a key SmallRye does not read leaves
 * the annotation values in place without an error, as the interface name did for the dev timeout.
 */
@QuarkusTest
@TestProfile(TriageFaultToleranceTest.Profile.class)
class TriageFaultToleranceTest {

  private static final int CONFIGURED_CONCURRENCY = 3;
  private static final long CONFIGURED_TIMEOUT_SECONDS = 30;

  /** A concurrency other than the annotation's bulkhead of two. */
  public static class Profile implements QuarkusTestProfile {
    @Override
    public Map<String, String> getConfigOverrides() {
      return Map.of(
          "incident.workflow.executor.max-concurrency", String.valueOf(CONFIGURED_CONCURRENCY));
    }
  }

  @Inject FaultToleranceOperationProvider operations;

  @Test
  void bulkheadAndTimeoutComeFromTheConfiguration() throws NoSuchMethodException {
    Class<?> beanClass = Arc.container().instance(TriageAssistant.class).getBean().getBeanClass();
    FaultToleranceOperation triage =
        operations.get(
            beanClass,
            beanClass.getMethod(
                "triage",
                String.class,
                String.class,
                String.class,
                String.class,
                String.class,
                String.class,
                String.class));

    assertThat(triage.getBulkhead().value()).isEqualTo(CONFIGURED_CONCURRENCY);
    assertThat(triage.getTimeout().value()).isEqualTo(CONFIGURED_TIMEOUT_SECONDS);
  }
}
