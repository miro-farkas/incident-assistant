package dev.mfa.incident.opstools.service;

import java.time.Duration;
import java.util.List;

/**
 * The fixed shape of a mock service: what it depends on, how many pods it runs and which releases
 * it had. The configured scenario decides how it behaves.
 *
 * @param dependencies downstream services, the first one is the one that times out in {@link
 *     dev.mfa.incident.opstools.config.Scenario#DOWNSTREAM_TIMEOUT}
 * @param releases release history, oldest first; the last one is running
 */
record ServiceProfile(
    String name,
    List<String> dependencies,
    int replicas,
    int maxReplicas,
    double baseRequestsPerSecond,
    List<Release> releases) {

  ServiceProfile {
    dependencies = List.copyOf(dependencies);
    releases = List.copyOf(releases);
  }

  Release runningRelease() {
    return releases.getLast();
  }

  /**
   * A rolled out release.
   *
   * @param age time between the rollout and the anchor time of the simulated timeline
   */
  record Release(String version, Duration age, String deployedBy, String change) {}
}
