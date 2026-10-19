package dev.mfa.incident.exception;

/**
 * Another transaction committed an open incident for the same source and fingerprint first (the
 * partial unique index of A-0002). The source and fingerprint are not part of the message.
 */
public final class OpenIncidentExistsException extends IncidentException {

  public OpenIncidentExistsException() {
    super("An open incident for this source and fingerprint already exists");
  }
}
