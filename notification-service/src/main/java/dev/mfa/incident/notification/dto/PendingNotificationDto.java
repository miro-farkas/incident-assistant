package dev.mfa.incident.notification.dto;

import dev.mfa.incident.notification.model.ChannelType;
import java.time.Instant;
import java.util.UUID;

/**
 * A notification claimed by the sender.
 *
 * @param attempts attempts including the current one
 */
public record PendingNotificationDto(
    UUID id,
    UUID incidentId,
    String rule,
    String state,
    String reasonCode,
    Instant occurredAt,
    ChannelType channel,
    String recipient,
    int attempts) {}
