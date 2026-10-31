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
import dev.mfa.incident.dto.TriageResult;
import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.hibernate.validator.messageinterpolation.ParameterMessageInterpolator;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** The business rules of the triage answer (A-0014), checked without a model. */
class TriageOutputGuardrailTest {

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
  private static TriageOutputGuardrail guardrail;

  @BeforeAll
  static void createGuardrail() {
    validators =
        Validation.byDefaultProvider()
            .configure()
            .messageInterpolator(new ParameterMessageInterpolator())
            .buildValidatorFactory();
    guardrail = new TriageOutputGuardrail(new ObjectMapper(), validators.getValidator());
  }

  @AfterAll
  static void closeValidators() {
    validators.close();
  }

  @Test
  void validAnswerPassesAsTheMessageTheServiceParses() throws Exception {
    OutputGuardrailResult result =
        validate(answer("HIGH", "DEPLOYMENT", "checkout", "Errors after the release."), "Checkout");

    assertThat(result.isSuccess()).isTrue();
    assertThat(new ObjectMapper().readValue(result.successfulText(), TriageResult.class))
        .isEqualTo(
            new TriageResult(
                TriageResult.Severity.HIGH,
                TriageResult.Category.DEPLOYMENT,
                "checkout",
                "Errors after the release."));
  }

  @Test
  void codeFenceAroundTheJsonIsTolerated() {
    String fenced = "```json\n" + answer("LOW", "OTHER", "checkout", "Fine.") + "\n```";

    assertThat(validate(fenced, null).isSuccess()).isTrue();
  }

  @Test
  void invalidAnswersAreRepromptedWithTheReason() {
    assertReprompt(validate("Sure, here is the triage: HIGH", null), "not a JSON object");
    assertReprompt(validate(answer("URGENT", "OTHER", "checkout", "x"), null), "not a JSON object");
    assertReprompt(
        validate(answer("HIGH", "OTHER", "Checkout Service", "x"), null), "affectedService");
    assertReprompt(validate(answer("HIGH", "OTHER", "checkout", "x".repeat(501)), null), "summary");
    assertReprompt(
        validate(
            "{\"severity\": \"HIGH\", \"category\": \"OTHER\", \"affectedService\": \"checkout\","
                + " \"summary\": \"x\", \"confidence\": 0.9}",
            null),
        "not a JSON object");
  }

  @Test
  void affectedServiceMustBeTheServiceTheAlertReported() {
    assertReprompt(
        validate(answer("HIGH", "DEPENDENCY", "payments", "Bank timeouts."), "checkout"),
        "affectedService must be the service the alert names: checkout");
    assertThat(validate(answer("HIGH", "DEPENDENCY", "payments", "x"), "  ").isSuccess()).isTrue();
  }

  private static void assertReprompt(OutputGuardrailResult result, String reason) {
    assertThat(result.isSuccess()).isFalse();
    assertThat(result.isReprompt()).isTrue();
    assertThat(result.failures()).first().asString().contains(reason);
  }

  private static String answer(String severity, String category, String service, String summary) {
    return """
    {"severity": "%s", "category": "%s", "affectedService": "%s", "summary": "%s"}\
    """
        .formatted(severity, category, service, summary);
  }

  private static OutputGuardrailResult validate(String text, String reportedService) {
    Map<String, Object> variables = new HashMap<>();
    variables.put(TriageOutputGuardrail.REPORTED_SERVICE, reportedService);
    return guardrail.validate(
        OutputGuardrailRequest.builder()
            .responseFromLLM(ChatResponse.builder().aiMessage(AiMessage.from(text)).build())
            .chatExecutor(NO_CHAT)
            .requestParams(
                GuardrailRequestParams.builder()
                    .userMessageTemplate("")
                    .variables(variables)
                    .build())
            .build());
  }
}
