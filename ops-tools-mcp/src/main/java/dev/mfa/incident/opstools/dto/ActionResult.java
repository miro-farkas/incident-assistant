package dev.mfa.incident.opstools.dto;

import java.time.Instant;

/**
 * Result of an action. A repeated call with the same idempotency key returns the original result
 * with {@code replayed} set.
 *
 * @param actionId identifier of the execution, derived from the idempotency key
 * @param message what was done, or why the action was rejected
 * @param executedAt when the action was originally executed
 * @param replayed whether this is the stored result of an earlier call with the same key
 */
public record ActionResult(
    String actionId,
    ActionType action,
    String service,
    ActionStatus status,
    String message,
    String idempotencyKey,
    Instant executedAt,
    boolean replayed) {}
