package dev.mfa.incident.dto;

import dev.mfa.incident.model.Decision;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * An approver's decision on the pending remediation proposal (A-0006). Validated with Jakarta Bean
 * Validation; the approver is taken from the token, never from this record.
 *
 * @param comment optional reason, shown with the decision
 */
public record DecisionDto(
    @NotNull Decision decision, @Size(max = DecisionDto.MAX_COMMENT_LENGTH) String comment) {

  public static final int MAX_COMMENT_LENGTH = 2000;
}
