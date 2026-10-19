package dev.mfa.incident.opstools.exception;

/** An idempotency key was reused for a different action or different parameters. */
public final class IdempotencyConflictException extends OpsToolException {

  public IdempotencyConflictException(String idempotencyKey) {
    super(
        "Idempotency key '%s' was already used for a different action or different parameters."
            .formatted(idempotencyKey));
  }
}
