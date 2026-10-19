package dev.mfa.incident.opstools.exception;

import java.util.Collection;

/** The requested service is not known to the ops backend. */
public final class UnknownServiceException extends OpsToolException {

  public UnknownServiceException(String service, Collection<String> knownServices) {
    super(
        "Unknown service '%s'. Known services: %s"
            .formatted(service, String.join(", ", knownServices)));
  }
}
