package dev.mfa.incident.workflow;

import dev.mfa.incident.outbox.OutboxWriter;
import dev.mfa.incident.workflow.persistence.IncidentEntity;
import dev.mfa.incident.workflow.persistence.IncidentRepository;
import dev.mfa.incident.workflow.persistence.IncidentStepEntity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

/**
 * Creates incidents and records step results. Each method is one transaction that writes the state
 * change and its outbox event together (ADR-0003). Callers perform LLM and MCP calls before calling
 * {@link #recordStep}, outside any transaction.
 */
@ApplicationScoped
public class IncidentService {

  private final IncidentRepository incidents;
  private final OutboxWriter outbox;
  private final Clock clock;

  public IncidentService(IncidentRepository incidents, OutboxWriter outbox, Clock clock) {
    this.incidents = incidents;
    this.outbox = outbox;
    this.clock = clock;
  }

  /** Opens an incident in {@link IncidentStateMachine#INITIAL_STATE}. */
  @Transactional
  public Incident create(NewIncident newIncident) {
    Instant now = clock.instant();
    IncidentEntity entity = IncidentEntity.received(newIncident, now);
    incidents.persist(entity);
    incidents.flush();
    Incident incident = entity.toDomain();
    outbox.write(IncidentStateChanged.created(incident, now));
    return incident;
  }

  /**
   * @throws IncidentNotFoundException if no incident has this id
   */
  @Transactional
  public Incident get(UUID incidentId) {
    return incidents
        .findById(incidentId)
        .map(IncidentEntity::toDomain)
        .orElseThrow(() -> new IncidentNotFoundException(incidentId));
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
  public Incident recordStep(
      UUID incidentId,
      long expectedVersion,
      StepResult result,
      IncidentState targetState,
      ReasonCode reasonCode) {
    requireReasonCodeMatches(targetState, reasonCode);
    IncidentEntity entity =
        incidents.findById(incidentId).orElseThrow(() -> new IncidentNotFoundException(incidentId));
    if (entity.version() != expectedVersion) {
      throw new IncidentVersionConflictException(incidentId, expectedVersion);
    }
    IncidentState previousState = entity.state();
    if (result.type().sourceState() != previousState) {
      throw new StepNotAllowedException(result.type(), previousState);
    }

    Instant now = clock.instant();
    entity.transitionTo(targetState, now);
    IncidentStepEntity step =
        IncidentStepEntity.recorded(incidentId, result, previousState, targetState, reasonCode);
    incidents.persist(step);
    incidents.flush(entity, expectedVersion);

    Incident incident = entity.toDomain();
    outbox.write(
        IncidentStateChanged.transitioned(
            incident, previousState, result.type(), step.id(), reasonCode, now));
    return incident;
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
