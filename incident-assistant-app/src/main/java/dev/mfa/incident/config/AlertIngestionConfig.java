package dev.mfa.incident.config;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;
import jakarta.validation.constraints.Min;

/** Limits for alerts consumed from {@code ops.alerts.v1} (A-0003). */
@ConfigMapping(prefix = "incident.alerts")
public interface AlertIngestionConfig {

  /** Largest accepted message value in bytes; larger messages go to the DLQ without parsing. */
  @WithDefault("65536")
  @Min(1)
  int maxPayloadBytes();
}
