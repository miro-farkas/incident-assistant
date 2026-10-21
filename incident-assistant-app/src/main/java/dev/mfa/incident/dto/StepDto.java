package dev.mfa.incident.dto;

import com.fasterxml.jackson.annotation.JsonRawValue;
import dev.mfa.incident.model.IncidentState;
import dev.mfa.incident.model.StepStatus;
import dev.mfa.incident.model.StepType;
import java.time.Instant;
import java.util.UUID;
import org.eclipse.microprofile.openapi.annotations.enums.SchemaType;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * One executed step of an incident, as stored.
 *
 * @param output JSON document the step produced, as stored ({@code jsonb}); serialized as embedded
 *     JSON, {@code null} when the step produced nothing. Typed views are added by the items that
 *     define each step's result.
 * @param errorMessage diagnostic message of a failed step
 */
public record StepDto(
    UUID id,
    StepType type,
    StepStatus status,
    IncidentState fromState,
    IncidentState toState,
    Instant startedAt,
    Instant finishedAt,
    long durationMs,
    String reasonCode,
    String errorMessage,
    String modelId,
    String promptId,
    String promptVersion,
    Integer inputTokens,
    Integer outputTokens,
    @JsonRawValue
        @Schema(type = SchemaType.OBJECT, description = "Step output as stored, any JSON value")
        String output) {}
