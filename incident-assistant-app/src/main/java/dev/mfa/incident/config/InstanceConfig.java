package dev.mfa.incident.config;

import io.smallrye.config.ConfigMapping;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * Identity of this service instance. The id distinguishes instances in health output and logs and
 * is the owner recorded on work leases (ADR-0003), so it must be unique per running instance.
 */
@ConfigMapping(prefix = "incident.instance")
public interface InstanceConfig {

  /** Allowed instance id: alphanumeric start, then alphanumerics, '.', '_' or '-', 1-63 chars. */
  String ID_PATTERN = "[A-Za-z0-9][A-Za-z0-9._-]{0,62}";

  /** Unique id of this instance, for example the pod or host name. */
  @NotBlank
  @Pattern(regexp = ID_PATTERN)
  String id();
}
