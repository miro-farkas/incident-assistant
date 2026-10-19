package dev.mfa.incident.opstools.dto;

/** The remediation actions of the ops backend (A-0005). */
public enum ActionType {
  RESTART_SERVICE,
  ROLLBACK_DEPLOYMENT,
  SCALE_SERVICE
}
