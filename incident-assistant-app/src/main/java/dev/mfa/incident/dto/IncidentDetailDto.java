package dev.mfa.incident.dto;

import com.fasterxml.jackson.annotation.JsonRawValue;
import dev.mfa.incident.model.IncidentState;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.eclipse.microprofile.openapi.annotations.enums.SchemaType;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * An incident with everything an approver needs to decide (A-0006).
 *
 * @param alert the validated alert as stored ({@code jsonb}), serialized as embedded JSON
 * @param occurrenceCount repeated alerts received while the incident was open (A-0003)
 * @param steps executed steps in the order they ran
 * @param triage result of the latest successful triage step (A-0014); {@code null} before one
 * @param diagnosis result of the latest successful diagnosis step (A-0015); {@code null} before one
 * @param decision the approval decision; {@code null} until one is made
 */
public record IncidentDetailDto(
    UUID id,
    String source,
    String fingerprint,
    IncidentState state,
    long version,
    Instant createdAt,
    Instant updatedAt,
    @JsonRawValue
        @Schema(type = SchemaType.OBJECT, description = "The alert as received (alert-v1)")
        String alert,
    long occurrenceCount,
    List<StepDto> steps,
    TriageResult triage,
    Diagnosis diagnosis,
    ApprovalDecisionDto decision) {}
