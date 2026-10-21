package dev.mfa.incident.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.util.UUID;

/**
 * The decision of an approver on one remediation proposal (A-0006). {@code createdAt} is the time
 * of the decision. Rows are never updated: the repository only inserts them.
 */
@Entity
@Table(name = "approval_decision")
public class ApprovalDecision extends BaseEntity {

  @Column(name = "incident_id", nullable = false, updatable = false)
  private UUID incidentId;

  /** The proposal step decided on; at most one decision per proposal. */
  @Column(name = "proposal_step_id", nullable = false, updatable = false)
  private UUID proposalStepId;

  @Enumerated(EnumType.STRING)
  @Column(name = "decision", nullable = false, updatable = false, length = 16)
  private Decision decision;

  /** Subject ({@code sub} claim) of the approver's token. */
  @Column(name = "approver_subject", nullable = false, updatable = false, length = 255)
  private String approverSubject;

  @Column(name = "comment", updatable = false, length = 2000)
  private String comment;

  public UUID getIncidentId() {
    return incidentId;
  }

  public void setIncidentId(UUID incidentId) {
    this.incidentId = incidentId;
  }

  public UUID getProposalStepId() {
    return proposalStepId;
  }

  public void setProposalStepId(UUID proposalStepId) {
    this.proposalStepId = proposalStepId;
  }

  public Decision getDecision() {
    return decision;
  }

  public void setDecision(Decision decision) {
    this.decision = decision;
  }

  public String getApproverSubject() {
    return approverSubject;
  }

  public void setApproverSubject(String approverSubject) {
    this.approverSubject = approverSubject;
  }

  public String getComment() {
    return comment;
  }

  public void setComment(String comment) {
    this.comment = comment;
  }
}
