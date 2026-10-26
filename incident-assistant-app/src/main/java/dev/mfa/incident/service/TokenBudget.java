package dev.mfa.incident.service;

import dev.mfa.incident.config.WorkflowConfig;
import dev.mfa.incident.dto.ModelUsageDto;
import dev.mfa.incident.dto.ReasonCode;
import dev.mfa.incident.repository.IncidentStepRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import java.util.UUID;

/** The per-workflow token budget of P-0008 ({@code incident.workflow.token-budget}). */
@ApplicationScoped
public class TokenBudget {

  public static final ReasonCode TOKEN_BUDGET_EXCEEDED = new ReasonCode("TOKEN_BUDGET_EXCEEDED");

  private final IncidentStepRepository steps;
  private final long budget;

  public TokenBudget(IncidentStepRepository steps, WorkflowConfig config) {
    this.steps = steps;
    this.budget = config.tokenBudget();
  }

  /** Whether the recorded steps plus {@code usage} of the step about to be recorded exceed it. */
  @Transactional
  public boolean exceededWith(UUID incidentId, ModelUsageDto usage) {
    return steps.sumTokens(incidentId) + usage.totalTokens() > budget;
  }

  public long limit() {
    return budget;
  }
}
