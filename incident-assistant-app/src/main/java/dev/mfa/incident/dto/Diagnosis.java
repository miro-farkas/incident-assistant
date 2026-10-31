package dev.mfa.incident.dto;

import dev.langchain4j.model.output.structured.Description;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * Structured output of the diagnosis step (A-0015): the most likely cause of an incident with the
 * evidence for it. Stored as the output of the diagnosis step and returned with the incident
 * detail. Every field is LLM output and therefore untrusted text (P-0019).
 */
public record Diagnosis(
    @NotBlank
        @Size(max = MAX_HYPOTHESIS_LENGTH)
        @Description("The most likely cause of the incident, in one paragraph of plain text")
        String hypothesis,
    @NotNull @Description("How well the evidence supports the hypothesis") Confidence confidence,
    @NotNull
        @Size(min = 1, max = MAX_EVIDENCE)
        @Description("The facts the hypothesis rests on, each with the source it came from")
        List<@NotNull @Valid Evidence> evidence) {

  public static final int MAX_HYPOTHESIS_LENGTH = 1000;
  public static final int MAX_EVIDENCE = 8;
  public static final int MAX_STATEMENT_LENGTH = 500;
  public static final int MAX_SOURCE_LENGTH = 200;

  public enum Confidence {
    HIGH,
    MEDIUM,
    LOW
  }

  public enum SourceType {
    KNOWLEDGE,
    TOOL
  }

  /** One fact and where it was found. */
  public record Evidence(
      @NotBlank
          @Size(max = MAX_STATEMENT_LENGTH)
          @Description("One fact found during the diagnosis, in plain text")
          String statement,
      @NotNull
          @Description(
              "KNOWLEDGE for a document found with search_knowledge, TOOL for a result of an"
                  + " operations tool")
          SourceType sourceType,
      @NotBlank
          @Size(max = MAX_SOURCE_LENGTH)
          @Description(
              "For KNOWLEDGE the title of the document, for TOOL the name of the tool, exactly"
                  + " as given")
          String source) {}
}
