package dev.mfa.incident.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.mfa.incident.dto.DecisionDto;
import dev.mfa.incident.dto.IncidentDetailDto;
import dev.mfa.incident.dto.IncidentDto;
import dev.mfa.incident.exception.DecisionNotAllowedException;
import dev.mfa.incident.exception.IncidentNotFoundException;
import dev.mfa.incident.exception.IncidentVersionConflictException;
import dev.mfa.incident.model.Decision;
import dev.mfa.incident.model.IncidentState;
import dev.mfa.incident.model.OutboxEvent;
import dev.mfa.incident.model.StepType;
import dev.mfa.incident.testsupport.IncidentEventSchema;
import dev.mfa.incident.testsupport.WorkflowDriver;
import dev.mfa.incident.testsupport.WorkflowTables;
import dev.mfa.incident.testsupport.WorkflowTables.DecisionRow;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@QuarkusTest
class IncidentDecisionTest {

  private static final String APPROVER = "approver-subject";
  private static final long CONCURRENCY_TIMEOUT_SECONDS = 30;

  @Inject IncidentService service;
  @Inject IncidentQueryService queries;
  @Inject WorkflowDriver workflow;
  @Inject WorkflowTables tables;
  @Inject ObjectMapper objectMapper;

  private IncidentEventSchema eventSchema;

  @BeforeEach
  void loadSchema() {
    eventSchema = IncidentEventSchema.load(objectMapper);
  }

  @Test
  void approvalRecordsDecisionMovesToApprovedAndPublishesEvent() throws Exception {
    IncidentDto pending = workflow.awaitingApproval();

    IncidentDetailDto approved =
        service.decide(
            pending.id(),
            pending.version(),
            new DecisionDto(Decision.APPROVE, "Rollback is safe"),
            APPROVER);

    assertThat(approved.state()).isEqualTo(IncidentState.APPROVED);
    assertThat(approved.version()).isEqualTo(pending.version() + 1);
    assertThat(approved.decision().decision()).isEqualTo(Decision.APPROVE);
    assertThat(approved.decision().approverSubject()).isEqualTo(APPROVER);
    assertThat(approved.decision().comment()).isEqualTo("Rollback is safe");
    assertThat(approved.decision().decidedAt()).isNotNull();

    UUID proposalStepId = proposalStepId(approved);
    assertThat(approved.decision().proposalStepId()).isEqualTo(proposalStepId);
    assertThat(tables.decisions(pending.id()))
        .containsExactly(new DecisionRow(proposalStepId, "APPROVE", APPROVER, "Rollback is safe"));

    JsonNode event = lastEvent(pending.id());
    assertThat(event.get("previousState").asText()).isEqualTo("AWAITING_APPROVAL");
    assertThat(event.get("state").asText()).isEqualTo("APPROVED");
    assertThat(event.get("incidentVersion").asLong()).isEqualTo(approved.version());
    assertThat(event.get("step").isNull()).isTrue();
    assertThat(event.get("stepId").isNull()).isTrue();
    assertThat(event.get("reasonCode").isNull()).isTrue();
  }

  @Test
  void rejectionMovesToRejectedWithReasonCode() throws Exception {
    IncidentDto pending = workflow.awaitingApproval();

    IncidentDetailDto rejected =
        service.decide(
            pending.id(), pending.version(), new DecisionDto(Decision.REJECT, null), APPROVER);

    assertThat(rejected.state()).isEqualTo(IncidentState.REJECTED);
    assertThat(rejected.decision().decision()).isEqualTo(Decision.REJECT);
    assertThat(rejected.decision().comment()).isNull();
    JsonNode event = lastEvent(pending.id());
    assertThat(event.get("state").asText()).isEqualTo("REJECTED");
    assertThat(event.get("reasonCode").asText())
        .isEqualTo(IncidentService.REJECTED_BY_APPROVER.value());
  }

  @Test
  void decisionOutsideAwaitingApprovalIsRefusedWithoutWritingAnything() {
    IncidentDto triaged = workflow.triaged();
    int events = tables.outboxEvents(triaged.id()).size();

    assertThatThrownBy(() -> service.decide(triaged.id(), triaged.version(), approve(), APPROVER))
        .isInstanceOfSatisfying(
            DecisionNotAllowedException.class,
            e -> assertThat(e.state()).isEqualTo(IncidentState.TRIAGED));

    assertThat(queries.detail(triaged.id()).state()).isEqualTo(IncidentState.TRIAGED);
    assertThat(tables.decisions(triaged.id())).isEmpty();
    assertThat(tables.outboxEvents(triaged.id())).hasSize(events);
  }

  @Test
  void secondDecisionOnDecidedIncidentIsRefused() {
    IncidentDto pending = workflow.awaitingApproval();
    IncidentDetailDto approved =
        service.decide(pending.id(), pending.version(), approve(), APPROVER);

    assertThatThrownBy(() -> service.decide(pending.id(), approved.version(), approve(), APPROVER))
        .isInstanceOf(DecisionNotAllowedException.class);
    assertThat(tables.decisions(pending.id())).hasSize(1);
  }

  @Test
  void staleVersionIsAConflict() {
    IncidentDto pending = workflow.awaitingApproval();

    assertThatThrownBy(
            () -> service.decide(pending.id(), pending.version() - 1, approve(), APPROVER))
        .isInstanceOf(IncidentVersionConflictException.class);
    assertThat(tables.decisions(pending.id())).isEmpty();
  }

  @Test
  void unknownIncidentIsNotFound() {
    UUID unknown = UUID.randomUUID();

    assertThatThrownBy(() -> service.decide(unknown, 0, approve(), APPROVER))
        .isInstanceOf(IncidentNotFoundException.class);
  }

  @Test
  void approverIsRequired() {
    IncidentDto pending = workflow.awaitingApproval();

    assertThatThrownBy(() -> service.decide(pending.id(), pending.version(), approve(), " "))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void concurrentDecisionsLetExactlyOneWin() throws Exception {
    IncidentDto pending = workflow.awaitingApproval();
    CountDownLatch start = new CountDownLatch(1);
    Callable<IncidentDetailDto> approve =
        () -> {
          start.await();
          return service.decide(pending.id(), pending.version(), approve(), APPROVER);
        };
    Callable<IncidentDetailDto> reject =
        () -> {
          start.await();
          return service.decide(
              pending.id(), pending.version(), new DecisionDto(Decision.REJECT, null), APPROVER);
        };

    int succeeded = 0;
    int conflicts = 0;
    try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
      Future<IncidentDetailDto> first = executor.submit(approve);
      Future<IncidentDetailDto> second = executor.submit(reject);
      start.countDown();
      for (Future<IncidentDetailDto> attempt : List.of(first, second)) {
        try {
          attempt.get(CONCURRENCY_TIMEOUT_SECONDS, TimeUnit.SECONDS);
          succeeded++;
        } catch (ExecutionException e) {
          assertThat(e.getCause()).isInstanceOf(IncidentVersionConflictException.class);
          conflicts++;
        }
      }
    }

    assertThat(succeeded).isEqualTo(1);
    assertThat(conflicts).isEqualTo(1);
    assertThat(tables.decisions(pending.id())).hasSize(1);
    assertThat(queries.detail(pending.id()).version()).isEqualTo(pending.version() + 1);
  }

  private static DecisionDto approve() {
    return new DecisionDto(Decision.APPROVE, null);
  }

  private static UUID proposalStepId(IncidentDetailDto incident) {
    return incident.steps().stream()
        .filter(step -> step.type() == StepType.PROPOSAL)
        .findFirst()
        .orElseThrow()
        .id();
  }

  private JsonNode lastEvent(UUID incidentId) throws JsonProcessingException {
    List<OutboxEvent> events = tables.outboxEvents(incidentId);
    JsonNode payload = objectMapper.readTree(events.getLast().getPayload());
    eventSchema.assertConforms(payload);
    return payload;
  }
}
