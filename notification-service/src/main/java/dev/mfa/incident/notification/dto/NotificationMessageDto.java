package dev.mfa.incident.notification.dto;

/**
 * A rendered message for one recipient.
 *
 * @param html the same content as {@code text}, escaped HTML
 */
public record NotificationMessageDto(String recipient, String subject, String text, String html) {}
