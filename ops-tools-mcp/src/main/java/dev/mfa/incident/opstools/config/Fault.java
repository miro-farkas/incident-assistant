package dev.mfa.incident.opstools.config;

/** A failure of the mock operations backend itself, independent of the simulated scenario. */
public enum Fault {
  /** Tools answer normally. */
  NONE,
  /** Every tool call for the service fails with a tool error, as if the backend were down. */
  UNAVAILABLE,
  /** Every tool call for the service is delayed by {@code ops.faults.slow-delay}. */
  SLOW
}
