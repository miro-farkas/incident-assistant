package dev.mfa.incident.ai;

import dev.langchain4j.service.Result;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.guardrail.OutputGuardrails;
import dev.mfa.incident.dto.Diagnosis;
import io.quarkiverse.langchain4j.RegisterAiService;
import jakarta.enterprise.context.ApplicationScoped;
import java.time.temporal.ChronoUnit;
import org.eclipse.microprofile.faulttolerance.Bulkhead;
import org.eclipse.microprofile.faulttolerance.CircuitBreaker;
import org.eclipse.microprofile.faulttolerance.Timeout;

/**
 * Diagnosis of one triaged incident (A-0001, A-0015), the only autonomous step: it calls the read
 * tools of {@link DiagnosisTools} within the budget of the current {@link DiagnosisRun} and returns
 * a hypothesis with evidence. Must be called inside {@link DiagnosisRun#run}. Named model {@code
 * diagnosis}; prompts in {@code prompts/diagnosis/}. Application-scoped and without chat memory,
 * like {@link TriageAssistant}.
 *
 * <p>Fault tolerance (P-0008, values in {@code application.properties}): a timeout of the whole
 * call with all its tool calls, no retry, a bulkhead of the executor's concurrency and a circuit
 * breaker that an exceeded tool budget does not trip. The output guardrail allows one reprompt.
 */
@RegisterAiService(
    modelName = "diagnosis",
    chatMemoryProviderSupplier = RegisterAiService.NoChatMemoryProviderSupplier.class,
    toolProviderSupplier = DiagnosisTools.class,
    maxToolCallingRoundTrips = DiagnosisAgent.MAX_ROUND_TRIPS)
@ApplicationScoped
public interface DiagnosisAgent {

  /**
   * Upper bound of model round trips: the largest tool budget ({@code
   * incident.workflow.diagnosis.max-tool-calls} at most 20) plus the answer and its reprompt. The
   * budget ends a call earlier.
   */
  int MAX_ROUND_TRIPS = 22;

  @SystemMessage(fromResource = "prompts/diagnosis/system.txt")
  @UserMessage(fromResource = "prompts/diagnosis/user.txt")
  @OutputGuardrails(DiagnosisOutputGuardrail.class)
  @Timeout(value = 120, unit = ChronoUnit.SECONDS)
  @Bulkhead(2)
  @CircuitBreaker(
      requestVolumeThreshold = 8,
      failureRatio = 0.5,
      delay = 60_000,
      skipOn = ToolCallBudgetExceededException.class)
  Result<Diagnosis> diagnose(
      int maxToolCalls,
      String alertName,
      String source,
      String service,
      String severity,
      String summary,
      String description,
      String labels,
      String triageSeverity,
      String triageCategory,
      String affectedService,
      String triageSummary);
}
