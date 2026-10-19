package dev.mfa.incident.service;

import dev.mfa.incident.dto.OutboxRecord;
import dev.mfa.incident.exception.OutboxPublishException;

/** Sends outbox events to the broker. Implemented in the messaging layer. */
public interface OutboxPublisher {

  /**
   * Sends the record and blocks until the broker acknowledged it.
   *
   * @throws OutboxPublishException if the broker rejected it or did not acknowledge it in time; it
   *     may still arrive later
   */
  void publish(OutboxRecord outboxRecord);
}
