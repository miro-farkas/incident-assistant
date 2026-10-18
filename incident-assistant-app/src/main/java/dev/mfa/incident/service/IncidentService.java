package dev.mfa.incident.service;

import dev.mfa.incident.dto.IncidentDto;
import dev.mfa.incident.dto.NewIncidentDto;
import dev.mfa.incident.dto.ReasonCode;
import dev.mfa.incident.dto.StepResultDto;
import dev.mfa.incident.exception.IllegalStateTransitionException;
import dev.mfa.incident.exception.IncidentNotFoundException;
import dev.mfa.incident.exception.IncidentVersionConflictException;
import dev.mfa.incident.exception.StepNotAllowedException;
import dev.mfa.incident.mapper.IncidentEventMapper;
import dev.mfa.incident.mapper.IncidentMapper;
import dev.mfa.incident.mapper.IncidentStepMapper;
import dev.mfa.incident.model.Incident;
import dev.mfa.incident.model.IncidentState;
import dev.mfa.incident.model.IncidentStep;
import dev.mfa.incident.repository.IncidentRepository;
import dev.mfa.incident.repository.IncidentStepRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import java.util.UUID;

/**
 * Creates incidents and records step results. Each method is one transaction that writes the state
 * change and its outbox event together (P-0003). Callers perform LLM and MCP calls before calling
 * {@link #recordStep}, outside any transaction.
 */
@ApplicationScoped
public class IncidentService {

  private final IncidentRepository incidents;
  private final IncidentStepRepository steps;
  private final IncidentMapper incidentMapper;
  private final IncidentStepMapper stepMapper;
  private final IncidentEventMapper eventMapper;
  private final OutboxWriter outbox;

  public IncidentService(
      IncidentRepository incidents,
      IncidentStepRepository steps,
      IncidentMapper incidentMapper,
      IncidentStepMapper stepMapper,
      IncidentEventMapper eventMapper,
      OutboxWriter outbox) {
    this.incidents = incidents;
    this.steps = steps;
    this.incidentMapper = incidentMapper;
    this.stepMapper = stepMapper;
    this.eventMapper = eventMapper;
    this.outbox = outbox;
  }

  /** Opens an incident in {@link IncidentStateMachine#INITIAL_STATE}. */
  @Transactional
  public IncidentDto create(NewIncidentDto newIncident) {
    Incident incident = incidentMapper.toEntity(newIncident);
    incident.setState(IncidentStateMachine.INITIAL_STATE);
    incidents.persist(incident);
    incidents.flush();
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
   * Records a step result and moves the incident to {@code targetState}.
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
    IncidentStep step =
        stepMapper.toEntity(incidentId, result, previousState, targetState, reasonCode);
    steps.persist(step);
    incidents.flush(incident, expectedVersion);

    outbox.write(
        eventMapper.transitioned(incident, previousState, result.type(), step.getId(), reasonCode));
    return incidentMapper.toDto(incident);
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
