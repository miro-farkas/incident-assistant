package dev.mfa.incident.opstools.dto;

import java.time.Instant;

/**
 * One log line of a service.
 *
 * @param messageTruncated whether the message was cut to the maximum message length
 */
public record LogEntry(
    Instant timestamp, LogLevel level, String pod, String message, boolean messageTruncated) {}
