package dev.mfa.incident.service;

import dev.mfa.incident.dto.ClaimedIncidentDto;
import dev.mfa.incident.model.IncidentState;

/**
 * One explicit step of the incident workflow (A-0001), run by the {@link StepExecutor} for an
 * incident it holds the lease for. A step does its model or tool calls outside any transaction and
 * records exactly one result with {@link IncidentService#recordStep}; a failure moves the incident
 * to {@code ESCALATED} (or {@code FAILED}) with a reason code, never into a retry loop.
 */
public interface WorkflowStep {

  /** The state the step runs in ({@link IncidentStateMachine#sourceState}). */
  IncidentState sourceState();

  void run(ClaimedIncidentDto incident);
}
