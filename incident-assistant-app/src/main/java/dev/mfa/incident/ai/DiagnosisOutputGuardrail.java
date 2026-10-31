package dev.mfa.incident.ai;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.guardrail.OutputGuardrail;
import dev.langchain4j.guardrail.OutputGuardrailRequest;
import dev.langchain4j.guardrail.OutputGuardrailResult;
import dev.mfa.incident.dto.Diagnosis;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Checks the diagnosis (A-0015) beyond the JSON schema: valid values and lengths (Bean Validation
 * of {@link Diagnosis}), and every evidence source is something this run consulted: a {@code
 * KNOWLEDGE} source is the title of a document a knowledge search returned, a {@code TOOL} source
 * the name of a tool that answered without error. A violation asks the model once more with the
 * reason; the AI service fails after that ({@code quarkus.langchain4j.guardrails.max-retries}).
 *
 * <p>A valid answer is returned as the message without a code fence, not as a parsed object: for a
 * {@code Result} return type the AI service parses the message it gets back, which after a reprompt
 * is the second answer.
 */
@ApplicationScoped
public class DiagnosisOutputGuardrail implements OutputGuardrail {

  private static final String REPROMPT =
      "Answer again with one JSON object that follows the schema and the rules of the system"
          + " message exactly. Cite only documents and tools from this conversation.";

  private final ObjectMapper json;
  private final Validator validator;

  public DiagnosisOutputGuardrail(ObjectMapper objectMapper, Validator validator) {
    this.json = objectMapper.copy().enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
    this.validator = validator;
  }

  @Override
  public OutputGuardrailResult validate(OutputGuardrailRequest request) {
    String text = request.responseFromLLM().aiMessage().text();
    Diagnosis diagnosis;
    try {
      diagnosis = json.readValue(TriageOutputGuardrail.withoutCodeFence(text), Diagnosis.class);
    } catch (IOException | IllegalArgumentException e) {
      return reprompt("The answer is not a JSON object of the requested schema", REPROMPT);
    }
    Set<ConstraintViolation<Diagnosis>> violations = validator.validate(diagnosis);
    if (!violations.isEmpty()) {
      return reprompt(describe(violations), REPROMPT);
    }
    List<String> unknown = unconsultedSources(diagnosis, DiagnosisRun.current());
    if (!unknown.isEmpty()) {
      return reprompt(
          "Evidence cites sources this diagnosis did not consult: "
              + String.join(", ", unknown)
              + ". KNOWLEDGE sources must be titles returned by "
              + KnowledgeSearchTool.NAME
              + ", TOOL sources names of tools that returned a result.",
          REPROMPT);
    }
    return successWith(AiMessage.from(TriageOutputGuardrail.withoutCodeFence(text)));
  }

  static List<String> unconsultedSources(Diagnosis diagnosis, DiagnosisRun run) {
    Set<String> titles = run.documentTitles();
    Set<String> tools = run.answeredTools();
    List<String> unknown = new ArrayList<>();
    for (Diagnosis.Evidence evidence : diagnosis.evidence()) {
      Set<String> known = evidence.sourceType() == Diagnosis.SourceType.KNOWLEDGE ? titles : tools;
      if (!known.contains(evidence.source().strip())) {
        unknown.add(evidence.sourceType() + " '" + evidence.source().strip() + "'");
      }
    }
    return unknown;
  }

  private static String describe(Set<ConstraintViolation<Diagnosis>> violations) {
    return violations.stream()
        .sorted(Comparator.comparing(v -> v.getPropertyPath().toString()))
        .map(v -> v.getPropertyPath() + " " + v.getMessage())
        .collect(Collectors.joining("; ", "Invalid diagnosis: ", ""));
  }
}
