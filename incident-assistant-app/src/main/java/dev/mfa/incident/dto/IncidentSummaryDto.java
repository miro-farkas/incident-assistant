package dev.mfa.incident.dto;

import dev.mfa.incident.model.IncidentState;
import java.time.Instant;
import java.util.UUID;

/**
 * One incident of the list (A-0006): its workflow state and how often its alert repeated.
 *
 * @param occurrenceCount repeated alerts received while the incident was open (A-0003)
 */
public record IncidentSummaryDto(
    UUID id,
    String source,
    String fingerprint,
    IncidentState state,
    long version,
    Instant createdAt,
    Instant updatedAt,
    long occurrenceCount) {}
