package dev.mfa.incident.workflow;

import static dev.mfa.incident.workflow.IncidentState.APPROVED;
import static dev.mfa.incident.workflow.IncidentState.AWAITING_APPROVAL;
import static dev.mfa.incident.workflow.IncidentState.DIAGNOSED;
import static dev.mfa.incident.workflow.IncidentState.ESCALATED;
import static dev.mfa.incident.workflow.IncidentState.FAILED;
import static dev.mfa.incident.workflow.IncidentState.RECEIVED;
import static dev.mfa.incident.workflow.IncidentState.REJECTED;
import static dev.mfa.incident.workflow.IncidentState.RESOLVED;
import static dev.mfa.incident.workflow.IncidentState.TRIAGED;

import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * The single definition of allowed incident state transitions (ADR-0001, see the state diagram in
 * {@code docs/architecture.md}). Every state change is checked here.
 */
public final class IncidentStateMachine {

  /** State of every newly created incident. */
  public static final IncidentState INITIAL_STATE = RECEIVED;

  private static final Map<IncidentState, Set<IncidentState>> TRANSITIONS = transitions();

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

  /** Whether entering {@code state} must carry a {@link ReasonCode}. */
  public static boolean requiresReasonCode(IncidentState state) {
    return REASON_REQUIRED.contains(state);
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
}
