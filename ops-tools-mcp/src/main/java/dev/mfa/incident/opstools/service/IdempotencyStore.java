package dev.mfa.incident.opstools.service;

import dev.mfa.incident.opstools.config.OpsToolsConfig;
import dev.mfa.incident.opstools.dto.ActionResult;
import dev.mfa.incident.opstools.exception.IdempotencyCapacityException;
import dev.mfa.incident.opstools.exception.IdempotencyConflictException;
import jakarta.enterprise.context.ApplicationScoped;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.SequencedMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

/**
 * Results of executed actions by idempotency key (P-0006). Held in memory: the mock backend has no
 * persistent state, so records are lost on restart. Records expire after the configured retention,
 * and their number is capped.
 *
 * <p>One lock serialises all actions, so two concurrent calls with the same key execute once.
 * Actions are rare and fast in the mock, apart from a simulated slow backend.
 */
@ApplicationScoped
public class IdempotencyStore {

  private final Clock clock;
  private final Duration retention;
  private final int maxRecords;
  private final ReentrantLock lock = new ReentrantLock();

  /** Insertion order is expiry order, because the retention is the same for all records. */
  private final SequencedMap<String, StoredResult> records = new LinkedHashMap<>();

  public IdempotencyStore(OpsToolsConfig config, Clock clock) {
    this.clock = clock;
    this.retention = config.actions().idempotencyRetention();
    this.maxRecords = config.actions().maxIdempotencyRecords();
  }

  /**
   * Returns the stored result for {@code key} marked as replayed, or runs {@code action} and stores
   * its result. Nothing is stored when the action throws, so it can be retried with the same key.
   *
   * @param fingerprint identifies the action and its parameters; a stored result is only returned
   *     for the same fingerprint
   * @throws IdempotencyConflictException if the key was used with a different fingerprint
   * @throws IdempotencyCapacityException if the store is full of unexpired records
   */
  ActionResult execute(String key, String fingerprint, Supplier<ActionResult> action) {
    lock.lock();
    try {
      Instant now = clock.instant();
      removeExpired(now);
      StoredResult stored = records.get(key);
      if (stored != null) {
        if (!stored.fingerprint().equals(fingerprint)) {
          throw new IdempotencyConflictException(key);
        }
        return replay(stored.result());
      }
      if (records.size() >= maxRecords) {
        throw new IdempotencyCapacityException();
      }
      ActionResult result = action.get();
      records.put(key, new StoredResult(fingerprint, result, now.plus(retention)));
      return result;
    } finally {
      lock.unlock();
    }
  }

  private void removeExpired(Instant now) {
    Iterator<Map.Entry<String, StoredResult>> oldestFirst = records.entrySet().iterator();
    while (oldestFirst.hasNext() && !oldestFirst.next().getValue().expiresAt().isAfter(now)) {
      oldestFirst.remove();
    }
  }

  private static ActionResult replay(ActionResult original) {
    return new ActionResult(
        original.actionId(),
        original.action(),
        original.service(),
        original.status(),
        original.message(),
        original.idempotencyKey(),
        original.executedAt(),
        true);
  }

  private record StoredResult(String fingerprint, ActionResult result, Instant expiresAt) {}
}
