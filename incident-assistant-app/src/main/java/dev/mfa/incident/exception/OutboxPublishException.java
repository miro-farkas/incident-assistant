package dev.mfa.incident.exception;

import java.util.UUID;

/** The broker did not acknowledge an outbox event in time or rejected it. */
public final class OutboxPublishException extends RuntimeException {

  private final UUID eventId;

  public OutboxPublishException(UUID eventId, String reason, Throwable cause) {
    super("Event " + eventId + " was not published: " + reason, cause);
    this.eventId = eventId;
  }

  public UUID eventId() {
    return eventId;
  }
}
