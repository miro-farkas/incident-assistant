package dev.mfa.incident.opstools.dto;

/** Outcome of an action. */
public enum ActionStatus {
  /** The action was carried out. */
  COMPLETED,
  /** The action was refused because its parameters do not fit the service; nothing changed. */
  REJECTED
}
