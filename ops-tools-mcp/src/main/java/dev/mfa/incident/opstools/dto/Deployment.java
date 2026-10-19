package dev.mfa.incident.opstools.dto;

import java.time.Instant;

/**
 * A release of a service that was rolled out.
 *
 * @param change short description of what the release changed
 */
public record Deployment(
    String id, String version, Instant deployedAt, String deployedBy, String change) {}
