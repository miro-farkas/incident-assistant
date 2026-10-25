package dev.mfa.incident.service;

import dev.mfa.incident.dto.DecisionDto;
import dev.mfa.incident.dto.IncidentDetailDto;
import dev.mfa.incident.dto.IncidentDto;
import dev.mfa.incident.dto.NewIncidentDto;
import dev.mfa.incident.dto.ReasonCode;
import dev.mfa.incident.dto.StepResultDto;
import dev.mfa.incident.exception.DecisionNotAllowedException;
import dev.mfa.incident.exception.IllegalStateTransitionException;
import dev.mfa.incident.exception.IncidentNotFoundException;
import dev.mfa.incident.exception.IncidentVersionConflictException;
import dev.mfa.incident.exception.OpenIncidentExistsException;
import dev.mfa.incident.exception.StepNotAllowedException;
import dev.mfa.incident.mapper.ApprovalDecisionMapper;
import dev.mfa.incident.mapper.IncidentEventMapper;
import dev.mfa.incident.mapper.IncidentMapper;
import dev.mfa.incident.mapper.IncidentStepMapper;
import dev.mfa.incident.model.ApprovalDecision;
import dev.mfa.incident.model.Decision;
import dev.mfa.incident.model.Incident;
import dev.mfa.incident.model.IncidentState;
import dev.mfa.incident.model.IncidentStep;
import dev.mfa.incident.model.StepType;
import dev.mfa.incident.repository.ApprovalDecisionRepository;
import dev.mfa.incident.repository.IncidentRepository;
import dev.mfa.incident.repository.IncidentStepRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import java.util.Objects;
import java.util.UUID;

/**
 * Creates incidents, records step results and approval decisions. Each method is one transaction
 * that writes the state change and its outbox event together (P-0003). Callers perform LLM and MCP
 * calls before calling {@link #recordStep}, outside any transaction.
 */
@ApplicationScoped
public class IncidentService {

  /** Reason code of an incident rejected by its approver (A-0006, A-0007). */
  public static final ReasonCode REJECTED_BY_APPROVER = new ReasonCode("REJECTED_BY_APPROVER");

  private final IncidentRepository incidents;
  private final IncidentStepRepository steps;
  private final IncidentMapper incidentMapper;
  private final IncidentStepMapper stepMapper;
  private final IncidentEventMapper eventMapper;
  private final ApprovalDecisionRepository decisions;
  private final ApprovalDecisionMapper decisionMapper;
  private final OutboxWriter outbox;
  private final IncidentQueryService queries;

  public IncidentService(
      IncidentRepository incidents,
      IncidentStepRepository steps,
      IncidentMapper incidentMapper,
      IncidentStepMapper stepMapper,
      IncidentEventMapper eventMapper,
      ApprovalDecisionRepository decisions,
      ApprovalDecisionMapper decisionMapper,
      OutboxWriter outbox,
      IncidentQueryService queries) {
    this.incidents = incidents;
    this.steps = steps;
    this.incidentMapper = incidentMapper;
    this.stepMapper = stepMapper;
    this.eventMapper = eventMapper;
    this.decisions = decisions;
    this.decisionMapper = decisionMapper;
    this.outbox = outbox;
    this.queries = queries;
  }

  /**
   * Opens an incident in {@link IncidentStateMachine#INITIAL_STATE}.
   *
   * @throws OpenIncidentExistsException if an incident with the same source and fingerprint is
   *     still open; the transaction is then rolled back
   */
  @Transactional
  public IncidentDto create(NewIncidentDto newIncident) {
    Incident incident = incidentMapper.toEntity(newIncident);
    incident.setState(IncidentStateMachine.INITIAL_STATE);
    incidents.persistNew(incident);
    outbox.write(eventMapper.created(incident));
    return incidentMapper.toDto(incident);
  }

  /**
   * @throws IncidentNotFoundException if no incident has this id
   */
  @Transactional
  public IncidentDto get(UUID incidentId) {
    return incidentMapper.toDto(find(incidentId));
  }

  /**
   * Records a step result and moves the incident to {@code targetState}; releases the executor's
   * lease (A-0014).
   *
   * @param expectedVersion incident version the step was computed from
   * @param reasonCode required when {@code targetState} requires one (see {@link
   *     IncidentStateMachine#requiresReasonCode}), otherwise must be {@code null}
   * @return the incident after the transition
   * @throws IncidentNotFoundException if no incident has this id
   * @throws IncidentVersionConflictException if the incident changed since {@code expectedVersion}
   * @throws StepNotAllowedException if the step does not belong to the current state
   * @throws IllegalStateTransitionException if the state machine does not allow the transition
   */
  @Transactional
  public IncidentDto recordStep(
      UUID incidentId,
      long expectedVersion,
      StepResultDto result,
      IncidentState targetState,
      ReasonCode reasonCode) {
    requireReasonCodeMatches(targetState, reasonCode);
    Incident incident = find(incidentId);
    if (incident.getVersion() != expectedVersion) {
      throw new IncidentVersionConflictException(incidentId, expectedVersion);
    }
    IncidentState previousState = incident.getState();
    IncidentState requiredState = IncidentStateMachine.sourceState(result.type());
    if (previousState != requiredState) {
      throw new StepNotAllowedException(result.type(), previousState, requiredState);
    }
    IncidentStateMachine.requireTransition(previousState, targetState);

    incident.setState(targetState);
    incident.setLeaseOwner(null);
    incident.setLeaseExpiresAt(null);
    IncidentStep step =
        stepMapper.toEntity(incidentId, result, previousState, targetState, reasonCode);
    steps.persist(step);
    incidents.flush(incident, expectedVersion);

    outbox.write(
        eventMapper.transitioned(incident, previousState, result.type(), step.getId(), reasonCode));
    return incidentMapper.toDto(incident);
  }

  /**
   * Records an approver's decision on the pending remediation proposal and moves the incident to
   * {@code APPROVED} or {@code REJECTED} (reason code {@link #REJECTED_BY_APPROVER}). The event
   * carries no step: a decision is not a workflow step.
   *
   * @param expectedVersion incident version the approver saw
   * @param approverSubject subject of the approver's token
   * @return the incident after the decision, as the approval API shows it
   * @throws IncidentNotFoundException if no incident has this id
   * @throws IncidentVersionConflictException if the incident changed since {@code expectedVersion},
   *     including a concurrent decision
   * @throws DecisionNotAllowedException if the incident is not waiting for approval
   */
  @Transactional
  public IncidentDetailDto decide(
      UUID incidentId, long expectedVersion, DecisionDto decision, String approverSubject) {
    Objects.requireNonNull(decision, "decision");
    Objects.requireNonNull(decision.decision(), "decision.decision");
    if (approverSubject == null || approverSubject.isBlank()) {
      throw new IllegalArgumentException("approverSubject is required");
    }
    Incident incident = find(incidentId);
    if (incident.getVersion() != expectedVersion) {
      throw new IncidentVersionConflictException(incidentId, expectedVersion);
    }
    IncidentState previousState = incident.getState();
    if (previousState != IncidentState.AWAITING_APPROVAL) {
      throw new DecisionNotAllowedException(incidentId, previousState);
    }
    IncidentStep proposal =
        steps
            .findLatest(incidentId, StepType.PROPOSAL)
            .orElseThrow(
                () ->
                    new IllegalStateException(
                        "Incident " + incidentId + " awaits approval without a proposal step"));
    IncidentState targetState =
        decision.decision() == Decision.APPROVE ? IncidentState.APPROVED : IncidentState.REJECTED;
    ReasonCode reasonCode =
        IncidentStateMachine.requiresReasonCode(targetState) ? REJECTED_BY_APPROVER : null;
    IncidentStateMachine.requireTransition(previousState, targetState);

    ApprovalDecision recorded =
        decisionMapper.toEntity(incidentId, proposal.getId(), decision, approverSubject);
    decisions.persistNew(recorded, expectedVersion);
    incident.setState(targetState);
    incidents.flush(incident, expectedVersion);

    outbox.write(eventMapper.transitioned(incident, previousState, null, null, reasonCode));
    return queries.detail(incidentId);
  }

  private Incident find(UUID incidentId) {
    return incidents
        .findByIdOptional(incidentId)
        .orElseThrow(() -> new IncidentNotFoundException(incidentId));
  }

  private static void requireReasonCodeMatches(IncidentState targetState, ReasonCode reasonCode) {
    boolean required = IncidentStateMachine.requiresReasonCode(targetState);
    if (required && reasonCode == null) {
      throw new IllegalArgumentException(
          "Transition to " + targetState + " requires a reason code");
    }
    if (!required && reasonCode != null) {
      throw new IllegalArgumentException("Transition to " + targetState + " takes no reason code");
    }
  }
}
