package dev.mfa.incident.workflow;

import static dev.mfa.incident.workflow.WorkflowFixtures.ALERT_PAYLOAD;
import static dev.mfa.incident.workflow.WorkflowFixtures.MODEL_USAGE;
import static dev.mfa.incident.workflow.WorkflowFixtures.failed;
import static dev.mfa.incident.workflow.WorkflowFixtures.newIncident;
import static dev.mfa.incident.workflow.WorkflowFixtures.succeeded;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.mfa.incident.outbox.persistence.OutboxEventEntity;
import dev.mfa.incident.workflow.WorkflowTables.StepRow;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.util.ArrayList;
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
class IncidentServiceTest {

  private static final long FIRST_VERSION = 0;
  private static final ReasonCode TRIAGE_FAILED = new ReasonCode("TRIAGE_FAILED");
  private static final long CONCURRENCY_TIMEOUT_SECONDS = 30;

  @Inject IncidentService service;
  @Inject WorkflowTables tables;
  @Inject ObjectMapper objectMapper;

  private IncidentEventSchema eventSchema;

  @BeforeEach
  void loadSchema() {
    eventSchema = IncidentEventSchema.load(objectMapper);
  }

  @Test
  void createsReceivedIncidentWithCreationEvent() throws Exception {
    NewIncident newIncident = newIncident();

    Incident incident = service.create(newIncident);

    assertThat(incident.id()).isNotNull();
    assertThat(incident.state()).isEqualTo(IncidentState.RECEIVED);
    assertThat(incident.version()).isEqualTo(FIRST_VERSION);
    assertThat(incident.fingerprint()).isEqualTo(newIncident.fingerprint());
    assertThat(service.get(incident.id())).isEqualTo(incident);
    assertThat(json(tables.alertPayload(incident.id()))).isEqualTo(json(ALERT_PAYLOAD));

    List<OutboxEventEntity> events = tables.outboxEvents(incident.id());
    assertThat(events).hasSize(1);
    OutboxEventEntity event = events.getFirst();
    assertThat(event.eventType()).isEqualTo(IncidentStateChanged.EVENT_TYPE);
    assertThat(event.messageKey()).isEqualTo(incident.id().toString());
    assertThat(event.publishedAt()).isNull();

    JsonNode payload = conformingPayload(event);
    assertThat(payload.get("eventId").asText()).isEqualTo(event.eventId().toString());
    assertThat(payload.get("incidentId").asText()).isEqualTo(incident.id().toString());
    assertThat(payload.get("incidentVersion").asLong()).isEqualTo(FIRST_VERSION);
    assertThat(payload.get("previousState").isNull()).isTrue();
    assertThat(payload.get("state").asText()).isEqualTo("RECEIVED");
    assertThat(payload.get("step").isNull()).isTrue();
    assertThat(payload.get("stepId").isNull()).isTrue();
    assertThat(payload.get("reasonCode").isNull()).isTrue();
  }

  @Test
  void recordsStepAndTransitionInOneChange() throws Exception {
    Incident created = service.create(newIncident());

    Incident triaged =
        service.recordStep(
            created.id(),
            created.version(),
            succeeded(StepType.TRIAGE),
            IncidentState.TRIAGED,
            null);

    assertThat(triaged.state()).isEqualTo(IncidentState.TRIAGED);
    assertThat(triaged.version()).isEqualTo(FIRST_VERSION + 1);
    assertThat(triaged.updatedAt()).isAfterOrEqualTo(created.updatedAt());
    assertThat(service.get(created.id())).isEqualTo(triaged);

    List<StepRow> steps = tables.steps(created.id());
    assertThat(steps).hasSize(1);
    StepRow step = steps.getFirst();
    assertThat(step.stepType()).isEqualTo("TRIAGE");
    assertThat(step.status()).isEqualTo("SUCCEEDED");
    assertThat(step.fromState()).isEqualTo("RECEIVED");
    assertThat(step.toState()).isEqualTo("TRIAGED");
    assertThat(json(step.input())).isEqualTo(json(succeeded(StepType.TRIAGE).input()));
    assertThat(json(step.output())).isEqualTo(json(succeeded(StepType.TRIAGE).output()));
    assertThat(step.modelId()).isEqualTo(MODEL_USAGE.modelId());
    assertThat(step.promptId()).isEqualTo(MODEL_USAGE.promptId());
    assertThat(step.promptVersion()).isEqualTo(MODEL_USAGE.promptVersion());
    assertThat(step.inputTokens()).isEqualTo(MODEL_USAGE.inputTokens());
    assertThat(step.outputTokens()).isEqualTo(MODEL_USAGE.outputTokens());
    assertThat(step.durationMs()).isEqualTo(2350);
    assertThat(step.reasonCode()).isNull();
    assertThat(step.errorMessage()).isNull();

    List<OutboxEventEntity> events = tables.outboxEvents(created.id());
    assertThat(events).hasSize(2);
    JsonNode payload = conformingPayload(events.get(1));
    assertThat(payload.get("previousState").asText()).isEqualTo("RECEIVED");
    assertThat(payload.get("state").asText()).isEqualTo("TRIAGED");
    assertThat(payload.get("incidentVersion").asLong()).isEqualTo(triaged.version());
    assertThat(payload.get("step").asText()).isEqualTo("TRIAGE");
    assertThat(payload.get("stepId").asText()).isEqualTo(step.id().toString());
    assertThat(payload.get("reasonCode").isNull()).isTrue();
  }

  @Test
  void advancesThroughStepsToAwaitingApproval() throws Exception {
    Incident incident = service.create(newIncident());

    incident = record(incident, StepType.TRIAGE, IncidentState.TRIAGED);
    incident = record(incident, StepType.DIAGNOSIS, IncidentState.DIAGNOSED);
    incident = record(incident, StepType.PROPOSAL, IncidentState.AWAITING_APPROVAL);

    assertThat(incident.state()).isEqualTo(IncidentState.AWAITING_APPROVAL);
    assertThat(incident.version()).isEqualTo(FIRST_VERSION + 3);
    assertThat(tables.steps(incident.id()))
        .extracting(StepRow::stepType)
        .containsExactly("TRIAGE", "DIAGNOSIS", "PROPOSAL");

    List<JsonNode> payloads = payloads(incident.id());
    assertThat(payloads)
        .extracting(p -> p.get("state").asText())
        .containsExactly("RECEIVED", "TRIAGED", "DIAGNOSED", "AWAITING_APPROVAL");
    assertThat(payloads)
        .extracting(p -> p.get("incidentVersion").asLong())
        .containsExactly(0L, 1L, 2L, 3L);
  }

  @Test
  void escalatesFailedStepWithReasonCode() throws Exception {
    Incident created = service.create(newIncident());

    Incident escalated =
        service.recordStep(
            created.id(),
            created.version(),
            failed(StepType.TRIAGE, "Model call timed out after 30s"),
            IncidentState.ESCALATED,
            TRIAGE_FAILED);

    assertThat(escalated.state()).isEqualTo(IncidentState.ESCALATED);
    StepRow step = tables.steps(created.id()).getFirst();
    assertThat(step.status()).isEqualTo("FAILED");
    assertThat(step.output()).isNull();
    assertThat(step.modelId()).isNull();
    assertThat(step.reasonCode()).isEqualTo(TRIAGE_FAILED.value());
    assertThat(step.errorMessage()).isEqualTo("Model call timed out after 30s");

    JsonNode payload = conformingPayload(tables.outboxEvents(created.id()).get(1));
    assertThat(payload.get("state").asText()).isEqualTo("ESCALATED");
    assertThat(payload.get("reasonCode").asText()).isEqualTo(TRIAGE_FAILED.value());
  }

  @Test
  void rejectsIllegalTransitionWithoutWritingAnything() {
    Incident created = service.create(newIncident());

    assertThatThrownBy(
            () ->
                service.recordStep(
                    created.id(),
                    created.version(),
                    succeeded(StepType.TRIAGE),
                    IncidentState.DIAGNOSED,
                    null))
        .isInstanceOf(IllegalStateTransitionException.class);

    assertUnchanged(created);
  }

  @Test
  void rejectsStepThatDoesNotBelongToCurrentState() {
    Incident created = service.create(newIncident());

    assertThatThrownBy(
            () ->
                service.recordStep(
                    created.id(),
                    created.version(),
                    succeeded(StepType.DIAGNOSIS),
                    IncidentState.DIAGNOSED,
                    null))
        .isInstanceOfSatisfying(
            StepNotAllowedException.class,
            e -> {
              assertThat(e.stepType()).isEqualTo(StepType.DIAGNOSIS);
              assertThat(e.state()).isEqualTo(IncidentState.RECEIVED);
            });

    assertUnchanged(created);
  }

  @Test
  void rejectsStepsOnClosedIncident() {
    Incident created = service.create(newIncident());
    Incident escalated =
        service.recordStep(
            created.id(),
            created.version(),
            failed(StepType.TRIAGE, "Invalid model output"),
            IncidentState.ESCALATED,
            TRIAGE_FAILED);

    assertThatThrownBy(
            () ->
                service.recordStep(
                    escalated.id(),
                    escalated.version(),
                    succeeded(StepType.TRIAGE),
                    IncidentState.TRIAGED,
                    null))
        .isInstanceOf(StepNotAllowedException.class);

    assertUnchanged(escalated);
  }

  @Test
  void rejectsStaleVersionWithoutWritingAnything() {
    Incident created = service.create(newIncident());
    Incident triaged = record(created, StepType.TRIAGE, IncidentState.TRIAGED);

    assertThatThrownBy(
            () ->
                service.recordStep(
                    created.id(),
                    created.version(),
                    succeeded(StepType.DIAGNOSIS),
                    IncidentState.DIAGNOSED,
                    null))
        .isInstanceOfSatisfying(
            IncidentVersionConflictException.class,
            e -> {
              assertThat(e.incidentId()).isEqualTo(created.id());
              assertThat(e.expectedVersion()).isEqualTo(created.version());
            });

    assertUnchanged(triaged);
  }

  @Test
  void concurrentStepsForSameVersionLetExactlyOneWin() throws Exception {
    Incident created = service.create(newIncident());
    CountDownLatch start = new CountDownLatch(1);
    Callable<Incident> triage =
        () -> {
          start.await();
          return service.recordStep(
              created.id(),
              created.version(),
              succeeded(StepType.TRIAGE),
              IncidentState.TRIAGED,
              null);
        };

    int succeeded = 0;
    int conflicts = 0;
    try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
      Future<Incident> first = executor.submit(triage);
      Future<Incident> second = executor.submit(triage);
      start.countDown();
      for (Future<Incident> attempt : List.of(first, second)) {
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
    assertThat(service.get(created.id()).version()).isEqualTo(FIRST_VERSION + 1);
    assertThat(tables.steps(created.id())).hasSize(1);
    assertThat(tables.outboxEvents(created.id())).hasSize(2);
  }

  @Test
  void requiresReasonCodeForEscalation() {
    Incident created = service.create(newIncident());

    assertThatThrownBy(
            () ->
                service.recordStep(
                    created.id(),
                    created.version(),
                    failed(StepType.TRIAGE, "Invalid model output"),
                    IncidentState.ESCALATED,
                    null))
        .isInstanceOf(IllegalArgumentException.class);

    assertUnchanged(created);
  }

  @Test
  void rejectsReasonCodeForRegularTransition() {
    Incident created = service.create(newIncident());

    assertThatThrownBy(
            () ->
                service.recordStep(
                    created.id(),
                    created.version(),
                    succeeded(StepType.TRIAGE),
                    IncidentState.TRIAGED,
                    TRIAGE_FAILED))
        .isInstanceOf(IllegalArgumentException.class);

    assertUnchanged(created);
  }

  @Test
  void reportsUnknownIncident() {
    UUID unknown = UUID.randomUUID();

    assertThatThrownBy(() -> service.get(unknown))
        .isInstanceOfSatisfying(
            IncidentNotFoundException.class, e -> assertThat(e.incidentId()).isEqualTo(unknown));
    assertThatThrownBy(
            () ->
                service.recordStep(
                    unknown,
                    FIRST_VERSION,
                    succeeded(StepType.TRIAGE),
                    IncidentState.TRIAGED,
                    null))
        .isInstanceOf(IncidentNotFoundException.class);
  }

  private Incident record(Incident incident, StepType step, IncidentState target) {
    return service.recordStep(incident.id(), incident.version(), succeeded(step), target, null);
  }

  /** The incident, its steps and its events are exactly as they were before the failed call. */
  private void assertUnchanged(Incident before) {
    int expectedSteps = (int) before.version();
    int expectedEvents = expectedSteps + 1;

    assertThat(service.get(before.id())).isEqualTo(before);
    assertThat(tables.steps(before.id())).hasSize(expectedSteps);
    assertThat(tables.outboxEvents(before.id())).hasSize(expectedEvents);
  }

  private List<JsonNode> payloads(UUID incidentId) throws JsonProcessingException {
    List<JsonNode> payloads = new ArrayList<>();
    for (OutboxEventEntity event : tables.outboxEvents(incidentId)) {
      payloads.add(conformingPayload(event));
    }
    return payloads;
  }

  private JsonNode conformingPayload(OutboxEventEntity event) throws JsonProcessingException {
    JsonNode payload = json(event.payload());
    eventSchema.assertConforms(payload);
    return payload;
  }

  private JsonNode json(String text) throws JsonProcessingException {
    return objectMapper.readTree(text);
  }
}
