package dev.mfa.incident.dto;

import java.util.UUID;

/**
 * A pending outbox event as handed to the publisher: the stored key and value, unchanged.
 *
 * @param sequenceNumber relay order (P-0003)
 * @param key Kafka message key
 * @param payload message value, exactly as written to the outbox
 */
public record OutboxRecord(UUID eventId, long sequenceNumber, String key, String payload) {}
