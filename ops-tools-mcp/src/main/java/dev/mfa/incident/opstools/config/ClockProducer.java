package dev.mfa.incident.opstools.config;

import jakarta.enterprise.inject.Produces;
import jakarta.inject.Singleton;
import java.time.Clock;

/** The clock for result timestamps and idempotency expiry. */
public class ClockProducer {

  @Produces
  @Singleton
  Clock clock() {
    return Clock.systemUTC();
  }
}
