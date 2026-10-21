package dev.mfa.incident.mapper;

import dev.mfa.incident.dto.ApprovalDecisionDto;
import dev.mfa.incident.dto.DecisionDto;
import dev.mfa.incident.model.ApprovalDecision;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.UUID;

@ApplicationScoped
public class ApprovalDecisionMapper {

  public ApprovalDecision toEntity(
      UUID incidentId, UUID proposalStepId, DecisionDto decision, String approverSubject) {
    ApprovalDecision entity = new ApprovalDecision();
    entity.setIncidentId(incidentId);
    entity.setProposalStepId(proposalStepId);
    entity.setDecision(decision.decision());
    entity.setApproverSubject(approverSubject);
    entity.setComment(decision.comment());
    return entity;
  }

  public ApprovalDecisionDto toDto(ApprovalDecision decision) {
    return new ApprovalDecisionDto(
        decision.getId(),
        decision.getProposalStepId(),
        decision.getDecision(),
        decision.getApproverSubject(),
        decision.getComment(),
        decision.getCreatedAt());
  }
}
