package dev.mfa.incident.testsupport;

import static dev.mfa.incident.testsupport.WorkflowFixtures.newIncident;
import static dev.mfa.incident.testsupport.WorkflowFixtures.succeeded;

import dev.mfa.incident.dto.IncidentDto;
import dev.mfa.incident.model.IncidentState;
import dev.mfa.incident.model.StepType;
import dev.mfa.incident.service.IncidentService;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * Moves new incidents through the workflow with the service layer, as the step executor will, so
 * tests can start from any state without an endpoint or shortcut that sets states.
 */
@ApplicationScoped
public class WorkflowDriver {

  private final IncidentService service;

  public WorkflowDriver(IncidentService service) {
    this.service = service;
  }

  /** A new incident after successful triage, diagnosis and proposal. */
  public IncidentDto awaitingApproval() {
    IncidentDto incident = service.create(newIncident());
    incident = advance(incident, StepType.TRIAGE, IncidentState.TRIAGED);
    incident = advance(incident, StepType.DIAGNOSIS, IncidentState.DIAGNOSED);
    return advance(incident, StepType.PROPOSAL, IncidentState.AWAITING_APPROVAL);
  }

  /** A new incident after successful triage only. */
  public IncidentDto triaged() {
    return advance(service.create(newIncident()), StepType.TRIAGE, IncidentState.TRIAGED);
  }

  private IncidentDto advance(IncidentDto incident, StepType step, IncidentState target) {
    return service.recordStep(incident.id(), incident.version(), succeeded(step), target, null);
  }
}
