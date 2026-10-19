package dev.mfa.incident.opstools.config;

/**
 * The situation a mock service simulates (A-0005). Every read tool derives its data from the
 * scenario and the anchor time only, so the same configuration always yields the same answers.
 */
public enum Scenario {
  /** Steady metrics, informational logs, no recent deployment. */
  HEALTHY,
  /** A deployment shortly before the anchor time raised the error rate and latency. */
  BAD_DEPLOYMENT,
  /** Heap usage grows until pods are killed out of memory; no recent deployment. */
  MEMORY_LEAK,
  /** A downstream dependency stopped answering; requests fail after the client timeout. */
  DOWNSTREAM_TIMEOUT
}
