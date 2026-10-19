package dev.mfa.incident.opstools.dto;

import java.time.Instant;

/**
 * Service-wide metrics at one point in time.
 *
 * @param cpuPercent average CPU usage of the service's pods
 * @param memoryPercent average heap usage of the service's pods
 * @param requestsPerSecond incoming request rate
 * @param errorRatePercent share of requests answered with a server error
 * @param latencyP99Ms 99th percentile response time
 */
public record MetricPoint(
    Instant timestamp,
    double cpuPercent,
    double memoryPercent,
    double requestsPerSecond,
    double errorRatePercent,
    long latencyP99Ms) {}
