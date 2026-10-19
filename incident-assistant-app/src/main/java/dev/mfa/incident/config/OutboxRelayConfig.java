package dev.mfa.incident.config;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.time.Duration;
import org.hibernate.validator.constraints.time.DurationMin;

/** The relay that publishes outbox events to Kafka (P-0003). */
@ConfigMapping(prefix = "incident.outbox.relay")
public interface OutboxRelayConfig {

  /** Pause between relay runs; a run publishes until no event is pending. */
  @DurationMin(millis = 1)
  Duration interval();

  /** Events read and published per transaction. */
  @WithDefault("100")
  @Min(1)
  @Max(1000)
  int batchSize();

  /** Longest wait for the broker to acknowledge one event; the run stops at the first miss. */
  @WithDefault("10s")
  @DurationMin(millis = 1)
  Duration sendTimeout();

  /**
   * No new send starts in a batch after this time; the batch commits and the next one continues.
   * Bounds how long a slow broker keeps the batch transaction open.
   */
  @WithDefault("10s")
  @DurationMin(millis = 1)
  Duration maxBatchDuration();
}
