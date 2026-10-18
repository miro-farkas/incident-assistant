package dev.mfa.incident.outbox;

import java.util.UUID;

/**
 * An event to be published through the transactional outbox. Implementations are serialized to JSON
 * as the message value; {@link #messageKey()} is routing metadata and not part of the value.
 */
public interface OutboxEvent {

  /** Unique id; consumers deduplicate on it. */
  UUID eventId();

  String eventType();

  /** Kafka message key. */
  String messageKey();
}
