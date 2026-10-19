package dev.mfa.incident.opstools.config;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;
import io.smallrye.config.WithDefaults;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import org.hibernate.validator.constraints.time.DurationMax;
import org.hibernate.validator.constraints.time.DurationMin;

/** The mock operations backend behind the MCP tools (A-0005). */
@ConfigMapping(prefix = "ops")
public interface OpsToolsConfig {

  /**
   * Behaviour per mock service, keyed by service name. Only services of the built-in catalogue are
   * allowed (checked at startup); a catalogued service without an entry is {@link Scenario#HEALTHY}
   * without fault.
   */
  @WithDefaults
  Map<String, ServiceSettings> services();

  FakeData fakeData();

  Faults faults();

  Actions actions();

  /** Behaviour of one mock service. */
  interface ServiceSettings {

    @WithDefault("healthy")
    Scenario scenario();

    @WithDefault("none")
    Fault fault();
  }

  /** Time base of the generated data. */
  interface FakeData {

    /**
     * Fixed "now" of the simulated timeline. Without it the timeline ends at the current minute of
     * each call; tests set it so timestamps are reproducible.
     */
    Optional<Instant> anchor();
  }

  /** Simulated backend failures. */
  interface Faults {

    /** Delay of every call for a service with {@link Fault#SLOW}. */
    @WithDefault("5s")
    @DurationMin(millis = 1)
    @DurationMax(minutes = 5)
    Duration slowDelay();
  }

  /** Action tools and their access (P-0006). */
  interface Actions {

    /** Key that clients of the action endpoint send in header {@code X-Api-Key}. */
    @Size(min = 32, max = 256)
    String apiKey();

    /** How long the result of an action stays available for replay under its idempotency key. */
    @WithDefault("24h")
    @DurationMin(minutes = 1)
    Duration idempotencyRetention();

    /**
     * Idempotency records kept at most. When the limit is reached and no record has expired, new
     * actions are refused rather than forgetting a record that is still within its retention.
     */
    @WithDefault("10000")
    @Min(1)
    @Max(1_000_000)
    int maxIdempotencyRecords();
  }
}
