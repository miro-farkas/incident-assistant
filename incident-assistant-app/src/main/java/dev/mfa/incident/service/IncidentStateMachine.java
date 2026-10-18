package dev.mfa.incident.service;

import static dev.mfa.incident.model.IncidentState.APPROVED;
import static dev.mfa.incident.model.IncidentState.AWAITING_APPROVAL;
import static dev.mfa.incident.model.IncidentState.DIAGNOSED;
import static dev.mfa.incident.model.IncidentState.ESCALATED;
import static dev.mfa.incident.model.IncidentState.FAILED;
import static dev.mfa.incident.model.IncidentState.RECEIVED;
import static dev.mfa.incident.model.IncidentState.REJECTED;
import static dev.mfa.incident.model.IncidentState.RESOLVED;
import static dev.mfa.incident.model.IncidentState.TRIAGED;

import dev.mfa.incident.exception.IllegalStateTransitionException;
import dev.mfa.incident.model.IncidentState;
import dev.mfa.incident.model.StepType;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * The single definition of allowed incident state transitions (A-0001, see the state diagram in
 * {@code docs/architecture.md}) and of the state each step runs in. Every state change is checked
 * here.
 */
public final class IncidentStateMachine {

  /** State of every newly created incident. */
  public static final IncidentState INITIAL_STATE = RECEIVED;

  private static final Map<IncidentState, Set<IncidentState>> TRANSITIONS = transitions();

  private static final Map<StepType, IncidentState> STEP_SOURCE_STATES = stepSourceStates();

  private static final Set<IncidentState> REASON_REQUIRED =
      Collections.unmodifiableSet(EnumSet.of(ESCALATED, FAILED, REJECTED));

  private IncidentStateMachine() {}

  /** Returns the states reachable from {@code from} in one transition. */
  public static Set<IncidentState> allowedTargets(IncidentState from) {
    return TRANSITIONS.get(from);
  }

  public static boolean isAllowed(IncidentState from, IncidentState to) {
    return allowedTargets(from).contains(to);
  }

  /**
   * @throws IllegalStateTransitionException if the transition is not allowed
   */
  public static void requireTransition(IncidentState from, IncidentState to) {
    if (!isAllowed(from, to)) {
      throw new IllegalStateTransitionException(from, to);
    }
  }

  /** A terminal state has no outgoing transitions; the incident is closed. */
  public static boolean isTerminal(IncidentState state) {
    return allowedTargets(state).isEmpty();
  }

  /** Whether entering {@code state} must carry a reason code. */
  public static boolean requiresReasonCode(IncidentState state) {
    return REASON_REQUIRED.contains(state);
  }

  /** The only state in which {@code step} may run. */
  public static IncidentState sourceState(StepType step) {
    return STEP_SOURCE_STATES.get(step);
  }

  private static Map<IncidentState, Set<IncidentState>> transitions() {
    Map<IncidentState, Set<IncidentState>> transitions = new EnumMap<>(IncidentState.class);
    transitions.put(RECEIVED, EnumSet.of(TRIAGED, ESCALATED));
    transitions.put(TRIAGED, EnumSet.of(DIAGNOSED, ESCALATED));
    transitions.put(DIAGNOSED, EnumSet.of(AWAITING_APPROVAL, ESCALATED));
    transitions.put(AWAITING_APPROVAL, EnumSet.of(APPROVED, REJECTED, ESCALATED));
    transitions.put(APPROVED, EnumSet.of(RESOLVED, FAILED));
    transitions.put(REJECTED, EnumSet.noneOf(IncidentState.class));
    transitions.put(RESOLVED, EnumSet.noneOf(IncidentState.class));
    transitions.put(ESCALATED, EnumSet.noneOf(IncidentState.class));
    transitions.put(FAILED, EnumSet.noneOf(IncidentState.class));
    transitions.replaceAll((state, targets) -> Collections.unmodifiableSet(targets));
    return Collections.unmodifiableMap(transitions);
  }

  private static Map<StepType, IncidentState> stepSourceStates() {
    Map<StepType, IncidentState> sources = new EnumMap<>(StepType.class);
    sources.put(StepType.TRIAGE, RECEIVED);
    sources.put(StepType.DIAGNOSIS, TRIAGED);
    sources.put(StepType.PROPOSAL, DIAGNOSED);
    sources.put(StepType.EXECUTION, APPROVED);
    return Collections.unmodifiableMap(sources);
  }
}
