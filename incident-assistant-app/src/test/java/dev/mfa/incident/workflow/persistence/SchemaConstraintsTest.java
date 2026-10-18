package dev.mfa.incident.workflow.persistence;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.mfa.incident.workflow.IncidentState;
import dev.mfa.incident.workflow.IncidentStateMachine;
import dev.mfa.incident.workflow.StepStatus;
import dev.mfa.incident.workflow.StepType;
import dev.mfa.incident.workflow.WorkflowFixtures;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import java.util.Arrays;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;

/** Keeps the constraints in V1__incident_schema.sql consistent with the Java enums. */
@QuarkusTest
class SchemaConstraintsTest {

  private static final String OPEN_INCIDENT_INDEX = "ux_incident_open_fingerprint";

  @Inject EntityManager entityManager;

  /**
   * The partial unique index must exclude exactly the terminal states of the state machine: a
   * second incident for the same alert is rejected while the first one is open, and allowed once it
   * is closed. Also proves that every state passes the state check constraint.
   */
  @ParameterizedTest
  @EnumSource(IncidentState.class)
  void allowsNewIncidentForSameAlertOnlyWhenPreviousIsClosed(IncidentState existingState) {
    String fingerprint = "fp-" + UUID.randomUUID();
    insertIncident(fingerprint, existingState);

    if (IncidentStateMachine.isTerminal(existingState)) {
      assertThatCode(() -> insertIncident(fingerprint, IncidentState.RECEIVED))
          .doesNotThrowAnyException();
    } else {
      assertThatThrownBy(() -> insertIncident(fingerprint, IncidentState.RECEIVED))
          .rootCause()
          .hasMessageContaining(OPEN_INCIDENT_INDEX);
    }
  }

  static Stream<Arguments> stepTypesAndStatuses() {
    return Arrays.stream(StepType.values())
        .flatMap(type -> Arrays.stream(StepStatus.values()).map(s -> Arguments.of(type, s)));
  }

  @ParameterizedTest
  @MethodSource("stepTypesAndStatuses")
  void acceptsEveryStepTypeAndStatus(StepType type, StepStatus status) {
    UUID incidentId = insertIncident("fp-" + UUID.randomUUID(), type.sourceState());

    assertThatCode(
            () ->
                QuarkusTransaction.requiringNew()
                    .run(
                        () ->
                            entityManager
                                .createNativeQuery(
                                    """
                                    insert into incident_step (id, incident_id, step_type, status,
                                        from_state, to_state, input, started_at, finished_at,
                                        duration_ms)
                                    values (:id, :incidentId, :type, :status, :from, :from,
                                        cast('{}' as jsonb), now(), now(), 0)
                                    """)
                                .setParameter("id", UUID.randomUUID())
                                .setParameter("incidentId", incidentId)
                                .setParameter("type", type.name())
                                .setParameter("status", status.name())
                                .setParameter("from", type.sourceState().name())
                                .executeUpdate()))
        .doesNotThrowAnyException();
  }

  private UUID insertIncident(String fingerprint, IncidentState state) {
    UUID id = UUID.randomUUID();
    QuarkusTransaction.requiringNew()
        .run(
            () ->
                entityManager
                    .createNativeQuery(
                        """
                        insert into incident (id, source, fingerprint, state, alert_payload,
                            version, created_at, updated_at)
                        values (:id, :source, :fingerprint, :state, cast(:payload as jsonb), 0,
                            now(), now())
                        """)
                    .setParameter("id", id)
                    .setParameter("source", WorkflowFixtures.SOURCE)
                    .setParameter("fingerprint", fingerprint)
                    .setParameter("state", state.name())
                    .setParameter("payload", WorkflowFixtures.ALERT_PAYLOAD)
                    .executeUpdate());
    return id;
  }
}
