package dev.mfa.incident.ai;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.guardrail.ChatExecutor;
import dev.langchain4j.guardrail.GuardrailRequestParams;
import dev.langchain4j.guardrail.OutputGuardrailRequest;
import dev.langchain4j.guardrail.OutputGuardrailResult;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.mfa.incident.dto.Diagnosis;
import dev.mfa.incident.dto.ToolCallDto;
import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import java.util.List;
import java.util.Map;
import org.hibernate.validator.messageinterpolation.ParameterMessageInterpolator;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** The business rules of the diagnosis (A-0015), checked without a model. */
class DiagnosisOutputGuardrailTest {

  private static final String POSTMORTEM = "Checkout bad deployment postmortem";

  /** The guardrail only judges the answer; a reprompt is sent by the framework, not by it. */
  private static final ChatExecutor NO_CHAT =
      new ChatExecutor() {
        @Override
        public ChatResponse execute() {
          throw new UnsupportedOperationException();
        }

        @Override
        public ChatResponse execute(List<ChatMessage> messages) {
          throw new UnsupportedOperationException();
        }
      };

  private static ValidatorFactory validators;
  private static DiagnosisOutputGuardrail guardrail;

  @BeforeAll
  static void createGuardrail() {
    validators =
        Validation.byDefaultProvider()
            .configure()
            .messageInterpolator(new ParameterMessageInterpolator())
            .buildValidatorFactory();
    guardrail = new DiagnosisOutputGuardrail(new ObjectMapper(), validators.getValidator());
  }

  @AfterAll
  static void closeValidators() {
    validators.close();
  }

  @Test
  void diagnosisCitingConsultedSourcesPasses() throws Exception {
    OutputGuardrailResult result =
        validate(
            consultedRun(),
            answer(
                "HIGH",
                evidence("Errors since the release", "TOOL", "get_recent_deployments"),
                evidence("Same pattern as before", "KNOWLEDGE", POSTMORTEM)));

    assertThat(result.isSuccess()).isTrue();
    Diagnosis diagnosis = new ObjectMapper().readValue(result.successfulText(), Diagnosis.class);
    assertThat(diagnosis.confidence()).isEqualTo(Diagnosis.Confidence.HIGH);
    assertThat(diagnosis.evidence()).hasSize(2);
  }

  @Test
  void invalidAnswersAreRepromptedWithTheReason() {
    DiagnosisRun run = consultedRun();
    assertReprompt(validate(run, "The cause is a bad deployment."), "not a JSON object");
    assertReprompt(
        validate(run, answer("SURE", evidence("x", "TOOL", "get_service_health"))),
        "not a JSON object");
    assertReprompt(validate(run, answer("HIGH")), "evidence size must be between 1 and 8");
    assertReprompt(
        validate(
            run,
            """
            {"hypothesis": "x", "confidence": "LOW", "evidence": [], "nextSteps": "restart"}\
            """),
        "not a JSON object");
  }

  @Test
  void evidenceMustCiteWhatTheRunConsulted() {
    DiagnosisRun run = consultedRun();

    assertReprompt(
        validate(run, answer("MEDIUM", evidence("Known issue", "KNOWLEDGE", "Some other runbook"))),
        "KNOWLEDGE 'Some other runbook'");
    assertReprompt(
        validate(run, answer("MEDIUM", evidence("Pods restart", "TOOL", "get_pod_events"))),
        "TOOL 'get_pod_events'");
    assertReprompt(
        validate(run, answer("MEDIUM", evidence("Logs show errors", "TOOL", "search_logs"))),
        "TOOL 'search_logs'");
    assertReprompt(
        validate(run, answer("MEDIUM", evidence("A document", "TOOL", POSTMORTEM))),
        "TOOL '" + POSTMORTEM + "'");
  }

  /** A run that searched the knowledge base, read deployments, and got an error from logs. */
  private static DiagnosisRun consultedRun() {
    DiagnosisRun run = new DiagnosisRun(6, "checkout");
    run.record(
        new ToolCallDto(
            KnowledgeSearchTool.NAME, "{}", ToolCallDto.Outcome.OK, 5, 100, List.of(POSTMORTEM)));
    run.record(
        new ToolCallDto("get_recent_deployments", "{}", ToolCallDto.Outcome.OK, 5, 100, null));
    run.record(new ToolCallDto("search_logs", "{}", ToolCallDto.Outcome.ERROR, 5, 20, null));
    return run;
  }

  private static void assertReprompt(OutputGuardrailResult result, String reason) {
    assertThat(result.isSuccess()).isFalse();
    assertThat(result.isReprompt()).isTrue();
    assertThat(result.failures()).first().asString().contains(reason);
  }

  private static String evidence(String statement, String sourceType, String source) {
    return """
    {"statement": "%s", "sourceType": "%s", "source": "%s"}\
    """
        .formatted(statement, sourceType, source);
  }

  private static String answer(String confidence, String... evidence) {
    return """
    {"hypothesis": "Release 2026.10.18-1 broke checkout.", "confidence": "%s", "evidence": [%s]}\
    """
        .formatted(confidence, String.join(", ", evidence));
  }

  private static OutputGuardrailResult validate(DiagnosisRun run, String text) {
    return run.run(
        () ->
            guardrail.validate(
                OutputGuardrailRequest.builder()
                    .responseFromLLM(ChatResponse.builder().aiMessage(AiMessage.from(text)).build())
                    .chatExecutor(NO_CHAT)
                    .requestParams(
                        GuardrailRequestParams.builder()
                            .userMessageTemplate("")
                            .variables(Map.of())
                            .build())
                    .build()));
  }
}
