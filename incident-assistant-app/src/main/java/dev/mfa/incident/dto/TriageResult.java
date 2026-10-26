package dev.mfa.incident.dto;

import dev.langchain4j.model.output.structured.Description;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Structured output of the triage step (A-0014): the model's assessment of an alert. Stored as the
 * output of the triage step and returned with the incident detail. Every field is LLM output and
 * therefore untrusted text (P-0019).
 */
public record TriageResult(
    @NotNull @Description("How urgently people must act, judged from the alert") Severity severity,
    @NotNull @Description("The kind of problem the alert most likely shows") Category category,
    @NotBlank
        @Pattern(regexp = SERVICE_PATTERN)
        @Description("Name of the affected service, lower case, for example checkout")
        String affectedService,
    @NotBlank
        @Size(max = MAX_SUMMARY_LENGTH)
        @Description("One paragraph of plain text: what is wrong and what is affected")
        String summary) {

  public static final String SERVICE_PATTERN = "[a-z][a-z0-9-]{0,62}";
  public static final int MAX_SUMMARY_LENGTH = 500;

  public enum Severity {
    CRITICAL,
    HIGH,
    MEDIUM,
    LOW
  }

  public enum Category {
    DEPLOYMENT,
    RESOURCE_EXHAUSTION,
    DEPENDENCY,
    CAPACITY,
    CONFIGURATION,
    OTHER
  }
}
