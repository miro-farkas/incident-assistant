package dev.mfa.incident.workflow;

import java.time.Instant;
import java.util.UUID;

/**
 * Workflow state of an incident. {@code version} is the optimistic-lock version for updates. The
 * alert payload is stored with the incident but not part of this snapshot.
 */
public record Incident(
    UUID id,
    String source,
    String fingerprint,
    IncidentState state,
    long version,
    Instant createdAt,
    Instant updatedAt) {}
