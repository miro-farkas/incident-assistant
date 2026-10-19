package dev.mfa.incident.opstools.dto;

/**
 * Health of a downstream dependency as seen by the calling service.
 *
 * @param latencyP99Ms 99th percentile latency of calls to the dependency
 */
public record DependencyHealth(String name, HealthStatus status, long latencyP99Ms) {}
