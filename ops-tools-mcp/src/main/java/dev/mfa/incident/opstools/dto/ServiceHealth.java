package dev.mfa.incident.opstools.dto;

import java.time.Instant;
import java.util.List;

/**
 * Current health of a service.
 *
 * @param version running release
 * @param restartsLastHour pod restarts within the last hour
 */
public record ServiceHealth(
    String service,
    HealthStatus status,
    String version,
    int replicasDesired,
    int replicasReady,
    int restartsLastHour,
    List<DependencyHealth> dependencies,
    Instant checkedAt) {

  public ServiceHealth {
    dependencies = List.copyOf(dependencies);
  }
}
