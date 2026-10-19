package dev.mfa.incident.opstools.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.mfa.incident.opstools.dto.ActionResult;
import dev.mfa.incident.opstools.dto.ActionStatus;
import dev.mfa.incident.opstools.dto.ActionType;
import dev.mfa.incident.opstools.exception.IdempotencyCapacityException;
import dev.mfa.incident.opstools.exception.IdempotencyConflictException;
import dev.mfa.incident.opstools.exception.UnknownServiceException;
import dev.mfa.incident.opstools.testsupport.TestConfig;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class IdempotencyStoreTest {

  private static final Duration RETENTION = Duration.ofHours(1);
  private static final String FINGERPRINT = "RESTART_SERVICE|checkout|";

  private final MutableClock clock = new MutableClock(Instant.parse("2026-10-18T10:00:00Z"));
  private final AtomicInteger executions = new AtomicInteger();

  @Test
  void firstCallExecutesAndRepeatedCallsReplay() {
    IdempotencyStore store = store(10);

    ActionResult first = store.execute("key-1", FINGERPRINT, this::execute);
    clock.advance(Duration.ofMinutes(5));
    ActionResult second = store.execute("key-1", FINGERPRINT, this::execute);

    assertThat(executions).hasValue(1);
    assertThat(first.replayed()).isFalse();
    assertThat(second.replayed()).isTrue();
    assertThat(second.executedAt()).isEqualTo(first.executedAt());
    assertThat(second.actionId()).isEqualTo(first.actionId());
  }

  @Test
  void otherFingerprintWithSameKeyConflicts() {
    IdempotencyStore store = store(10);
    store.execute("key-1", FINGERPRINT, this::execute);

    assertThatThrownBy(() -> store.execute("key-1", "SCALE_SERVICE|checkout|5", this::execute))
        .isInstanceOf(IdempotencyConflictException.class);
    assertThat(executions).hasValue(1);
  }

  @Test
  void failedActionIsNotStored() {
    IdempotencyStore store = store(10);

    assertThatThrownBy(
            () ->
                store.execute(
                    "key-1",
                    FINGERPRINT,
                    () -> {
                      throw new UnknownServiceException("shipping", List.of("checkout"));
                    }))
        .isInstanceOf(UnknownServiceException.class);

    assertThat(store.execute("key-1", FINGERPRINT, this::execute).replayed()).isFalse();
  }

  @Test
  void recordExpiresAfterTheRetention() {
    IdempotencyStore store = store(10);
    store.execute("key-1", FINGERPRINT, this::execute);

    clock.advance(RETENTION);

    assertThat(store.execute("key-1", FINGERPRINT, this::execute).replayed()).isFalse();
    assertThat(executions).hasValue(2);
  }

  @Test
  void fullStoreRefusesNewKeysUntilRecordsExpire() {
    IdempotencyStore store = store(2);
    store.execute("key-1", FINGERPRINT, this::execute);
    clock.advance(Duration.ofMinutes(1));
    store.execute("key-2", FINGERPRINT, this::execute);

    assertThatThrownBy(() -> store.execute("key-3", FINGERPRINT, this::execute))
        .isInstanceOf(IdempotencyCapacityException.class);
    assertThat(store.execute("key-2", FINGERPRINT, this::execute).replayed()).isTrue();

    clock.advance(RETENTION.minusMinutes(1));
    assertThat(store.execute("key-3", FINGERPRINT, this::execute).replayed()).isFalse();
    assertThat(store.execute("key-2", FINGERPRINT, this::execute).replayed()).isTrue();
  }

  @Test
  void concurrentCallsWithTheSameKeyExecuteOnce() throws Exception {
    IdempotencyStore store = store(10);
    Callable<ActionResult> call = () -> store.execute("key-1", FINGERPRINT, this::execute);

    try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
      List<Future<ActionResult>> results =
          IntStream.range(0, 20).mapToObj(i -> executor.submit(call)).toList();
      long originals = 0;
      for (Future<ActionResult> result : results) {
        originals += result.get().replayed() ? 0 : 1;
      }
      assertThat(originals).isEqualTo(1);
    }
    assertThat(executions).hasValue(1);
  }

  private IdempotencyStore store(int maxRecords) {
    return new IdempotencyStore(
        TestConfig.with(
            Map.of(
                "ops.actions.idempotency-retention", RETENTION.toString(),
                "ops.actions.max-idempotency-records", Integer.toString(maxRecords))),
        clock);
  }

  private ActionResult execute() {
    int execution = executions.incrementAndGet();
    return new ActionResult(
        "action-" + execution,
        ActionType.RESTART_SERVICE,
        "checkout",
        ActionStatus.COMPLETED,
        "Restarted",
        "key",
        clock.instant(),
        false);
  }

  private static final class MutableClock extends Clock {

    private Instant now;

    MutableClock(Instant now) {
      this.now = now;
    }

    void advance(Duration duration) {
      now = now.plus(duration);
    }

    @Override
    public ZoneId getZone() {
      return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(ZoneId zone) {
      throw new UnsupportedOperationException();
    }

    @Override
    public Instant instant() {
      return now;
    }
  }
}
