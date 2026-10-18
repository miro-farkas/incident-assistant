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
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;

/** Transitions copied from the state diagram in docs/architecture.md. */
class IncidentStateMachineTest {

  private static final Set<Transition> ALLOWED =
      Set.of(
          new Transition(RECEIVED, TRIAGED),
          new Transition(RECEIVED, ESCALATED),
          new Transition(TRIAGED, DIAGNOSED),
          new Transition(TRIAGED, ESCALATED),
          new Transition(DIAGNOSED, AWAITING_APPROVAL),
          new Transition(DIAGNOSED, ESCALATED),
          new Transition(AWAITING_APPROVAL, APPROVED),
          new Transition(AWAITING_APPROVAL, REJECTED),
          new Transition(AWAITING_APPROVAL, ESCALATED),
          new Transition(APPROVED, RESOLVED),
          new Transition(APPROVED, FAILED));

  private record Transition(IncidentState from, IncidentState to) {}

  static Stream<Arguments> allowedTransitions() {
    return ALLOWED.stream().map(t -> Arguments.of(t.from(), t.to()));
  }

  /** Every pair of states, including self-transitions, that is not in the diagram. */
  static Stream<Arguments> illegalTransitions() {
    return Arrays.stream(IncidentState.values())
        .flatMap(from -> Arrays.stream(IncidentState.values()).map(to -> new Transition(from, to)))
        .filter(t -> !ALLOWED.contains(t))
        .map(t -> Arguments.of(t.from(), t.to()));
  }

  @ParameterizedTest(name = "{0} -> {1}")
  @MethodSource("allowedTransitions")
  void allowsTransitionFromDiagram(IncidentState from, IncidentState to) {
    assertThat(IncidentStateMachine.isAllowed(from, to)).isTrue();
    assertThatCode(() -> IncidentStateMachine.requireTransition(from, to))
        .doesNotThrowAnyException();
  }

  @ParameterizedTest(name = "{0} -> {1}")
  @MethodSource("illegalTransitions")
  void rejectsTransitionNotInDiagram(IncidentState from, IncidentState to) {
    assertThat(IncidentStateMachine.isAllowed(from, to)).isFalse();
    assertThatThrownBy(() -> IncidentStateMachine.requireTransition(from, to))
        .isInstanceOfSatisfying(
            IllegalStateTransitionException.class,
            e -> {
              assertThat(e.from()).isEqualTo(from);
              assertThat(e.to()).isEqualTo(to);
            });
  }

  @Test
  void coversEveryPairOfStates() {
    int states = IncidentState.values().length;

    assertThat(allowedTransitions().count() + illegalTransitions().count())
        .isEqualTo((long) states * states);
  }

  @Test
  void terminalStatesAreTheEndStatesOfTheDiagram() {
    EnumSet<IncidentState> terminal = EnumSet.noneOf(IncidentState.class);
    for (IncidentState state : IncidentState.values()) {
      if (IncidentStateMachine.isTerminal(state)) {
        terminal.add(state);
      }
    }

    assertThat(terminal).containsExactlyInAnyOrder(REJECTED, RESOLVED, ESCALATED, FAILED);
  }

  @Test
  void newIncidentsStartReceived() {
    assertThat(IncidentStateMachine.INITIAL_STATE).isEqualTo(RECEIVED);
  }

  @ParameterizedTest
  @EnumSource(
      value = IncidentState.class,
      names = {"ESCALATED", "FAILED", "REJECTED"})
  void failureStatesRequireReasonCode(IncidentState state) {
    assertThat(IncidentStateMachine.requiresReasonCode(state)).isTrue();
  }

  @ParameterizedTest
  @EnumSource(
      value = IncidentState.class,
      names = {"ESCALATED", "FAILED", "REJECTED"},
      mode = EnumSource.Mode.EXCLUDE)
  void otherStatesTakeNoReasonCode(IncidentState state) {
    assertThat(IncidentStateMachine.requiresReasonCode(state)).isFalse();
  }

  @ParameterizedTest
  @EnumSource(StepType.class)
  void everyStepStartsFromANonTerminalState(StepType step) {
    assertThat(IncidentStateMachine.isTerminal(step.sourceState())).isFalse();
  }
}
