package dev.mfa.incident.opstools.dto;

import java.time.Instant;
import java.util.List;

/**
 * Metrics of one service over a time window, oldest point first.
 *
 * @param truncated whether the window held more points than returned; the newest are kept
 */
public record ServiceMetrics(
    String service,
    Instant from,
    Instant to,
    int stepMinutes,
    List<MetricPoint> points,
    boolean truncated) {

  public ServiceMetrics {
    points = List.copyOf(points);
  }
}
