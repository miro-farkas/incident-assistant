package dev.mfa.incident.ai;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.guardrail.OutputGuardrail;
import dev.langchain4j.guardrail.OutputGuardrailRequest;
import dev.langchain4j.guardrail.OutputGuardrailResult;
import dev.mfa.incident.dto.TriageResult;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import java.io.IOException;
import java.util.Comparator;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Checks the model's triage answer (A-0014) beyond the JSON schema: valid values and lengths (Bean
 * Validation of {@link TriageResult}), and the affected service equals the service the alert
 * reported, if it reported one. A violation asks the model once more with the reason; the AI
 * service fails after that ({@code quarkus.langchain4j.guardrails.max-retries}).
 */
@ApplicationScoped
public class TriageOutputGuardrail implements OutputGuardrail {

  /** Name of the user message variable with the service the alert reported. */
  static final String REPORTED_SERVICE = "service";

  private static final String REPROMPT =
      "Answer again with one JSON object that follows the schema and the rules of the system"
          + " message exactly.";

  private final ObjectMapper json;
  private final Validator validator;

  public TriageOutputGuardrail(ObjectMapper objectMapper, Validator validator) {
    this.json = objectMapper.copy().enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
    this.validator = validator;
  }

  @Override
  public OutputGuardrailResult validate(OutputGuardrailRequest request) {
    String text = request.responseFromLLM().aiMessage().text();
    TriageResult result;
    try {
      result = json.readValue(withoutCodeFence(text), TriageResult.class);
    } catch (IOException e) {
      return reprompt("The answer is not a JSON object of the requested schema", REPROMPT);
    }
    Set<ConstraintViolation<TriageResult>> violations = validator.validate(result);
    if (!violations.isEmpty()) {
      return reprompt(describe(violations), REPROMPT);
    }
    Object reported = request.requestParams().variables().get(REPORTED_SERVICE);
    if (reported instanceof String service
        && !service.isBlank()
        && !service.strip().equalsIgnoreCase(result.affectedService())) {
      return reprompt(
          "affectedService must be the service the alert names: "
              + service.strip().toLowerCase(Locale.ROOT),
          REPROMPT);
    }
    return successWith(text, result);
  }

  /** Small models sometimes wrap JSON in a Markdown code fence despite the output rules. */
  static String withoutCodeFence(String text) {
    String trimmed = text.strip();
    if (!trimmed.startsWith("```")) {
      return trimmed;
    }
    int start = trimmed.indexOf('\n');
    int end = trimmed.lastIndexOf("```");
    return start < 0 || end <= start ? trimmed : trimmed.substring(start + 1, end).strip();
  }

  private static String describe(Set<ConstraintViolation<TriageResult>> violations) {
    return violations.stream()
        .sorted(Comparator.comparing(v -> v.getPropertyPath().toString()))
        .map(v -> v.getPropertyPath() + " " + v.getMessage())
        .collect(Collectors.joining("; ", "Invalid triage result: ", ""));
  }
}
