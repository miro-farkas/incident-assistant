package dev.mfa.incident.opstools.exception;

/** No new action can be accepted because every idempotency record is still within its retention. */
public final class IdempotencyCapacityException extends OpsToolException {

  public IdempotencyCapacityException() {
    super("Too many recent actions; no new action is accepted until older ones expire.");
  }
}
