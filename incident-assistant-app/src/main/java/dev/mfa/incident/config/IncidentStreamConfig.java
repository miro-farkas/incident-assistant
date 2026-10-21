package dev.mfa.incident.config;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.time.Duration;
import org.hibernate.validator.constraints.time.DurationMin;

/** The Server-Sent Events stream of incident state events (A-0009, P-0018). */
@ConfigMapping(prefix = "incident.stream")
public interface IncidentStreamConfig {

  /** Interval of the heartbeat comment that keeps idle connections open through proxies. */
  @WithDefault("15s")
  @DurationMin(millis = 1)
  Duration heartbeatInterval();

  /** Events buffered for one client; a client that falls further behind is disconnected. */
  @WithDefault("256")
  @Min(1)
  @Max(10000)
  int clientBufferSize();

  /** Concurrent streams per instance; above it a new stream is refused with 503. */
  @WithDefault("200")
  @Min(1)
  int maxStreams();

  /** {@code Retry-After} sent when a stream is refused because of {@link #maxStreams()}. */
  @WithDefault("5s")
  @DurationMin(seconds = 1)
  Duration retryAfter();
}
