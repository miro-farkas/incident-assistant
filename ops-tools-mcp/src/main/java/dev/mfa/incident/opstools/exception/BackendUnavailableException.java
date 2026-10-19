package dev.mfa.incident.opstools.exception;

/** The ops backend of a service does not answer; the caller may retry later. */
public final class BackendUnavailableException extends OpsToolException {

  public BackendUnavailableException(String service) {
    super(
        "The operations backend for service '%s' is unavailable; retry later.".formatted(service));
  }
}
