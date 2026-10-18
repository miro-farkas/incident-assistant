package dev.mfa.incident.dto;

import java.util.UUID;

/**
 * A message to be published through the transactional outbox. Implementations are serialized to
 * JSON as the message value; {@link #messageKey()} is routing metadata and not part of the value.
 */
public interface OutboxMessage {

  /** Unique id; consumers deduplicate on it. */
  UUID eventId();

  String eventType();

  /** Kafka message key. */
  String messageKey();
}
