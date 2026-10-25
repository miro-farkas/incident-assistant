package dev.mfa.incident.dto;

import dev.mfa.incident.model.IncidentState;
import java.util.UUID;

/**
 * An incident the step executor holds the lease for (P-0003).
 *
 * @param version the version the step runs against; its result is recorded with this version
 * @param alertPayload the alert as stored, JSON
 */
public record ClaimedIncidentDto(UUID id, IncidentState state, long version, String alertPayload) {}
