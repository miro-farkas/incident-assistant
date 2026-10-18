package dev.mfa.incident.workflow;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Singleton;
import java.time.Clock;
import java.time.Duration;

/** Provides the UTC clock used for all persisted timestamps. */
@ApplicationScoped
public class ClockProducer {

  /** PostgreSQL {@code timestamptz} stores microseconds; finer ticks would not round-trip. */
  private static final Duration TICK = Duration.ofNanos(1_000);

  @Produces
  @Singleton
  Clock clock() {
    return Clock.tick(Clock.systemUTC(), TICK);
  }
}
