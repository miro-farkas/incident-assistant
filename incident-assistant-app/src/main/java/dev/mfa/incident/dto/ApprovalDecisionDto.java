package dev.mfa.incident.dto;

import dev.mfa.incident.model.Decision;
import java.time.Instant;
import java.util.UUID;

/**
 * A recorded decision on a remediation proposal.
 *
 * @param approverSubject subject ({@code sub} claim) of the approver's token
 */
public record ApprovalDecisionDto(
    UUID id,
    UUID proposalStepId,
    Decision decision,
    String approverSubject,
    String comment,
    Instant decidedAt) {}
