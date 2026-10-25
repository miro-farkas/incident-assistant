package dev.mfa.incident.notification.testsupport;

import io.quarkus.test.junit.QuarkusTestProfile;
import java.util.Map;

/**
 * The scheduled sender is off; tests call the sender and the delivery recorder themselves, so they
 * control every attempt.
 */
public class ManualSenderProfile implements QuarkusTestProfile {

  @Override
  public Map<String, String> getConfigOverrides() {
    return Map.of("quarkus.scheduler.enabled", "false");
  }
}
