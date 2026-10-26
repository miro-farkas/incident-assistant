package dev.mfa.incident.ai;

import dev.langchain4j.service.Result;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.guardrail.OutputGuardrails;
import dev.mfa.incident.dto.TriageResult;
import io.quarkiverse.langchain4j.RegisterAiService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.ProcessingException;
import java.time.temporal.ChronoUnit;
import org.eclipse.microprofile.faulttolerance.Bulkhead;
import org.eclipse.microprofile.faulttolerance.CircuitBreaker;
import org.eclipse.microprofile.faulttolerance.Retry;
import org.eclipse.microprofile.faulttolerance.Timeout;
import org.eclipse.microprofile.faulttolerance.exceptions.TimeoutException;

/**
 * Triage of one alert (A-0014): severity, category, affected service and summary. Named model
 * {@code triage}; prompts in {@code prompts/triage/}. Stateless: no memory between incidents, and
 * therefore application-scoped, because the step executor calls it from its own threads, where no
 * request context is active (the default scope of an AI service is the request).
 *
 * <p>Fault tolerance (P-0008, values in {@code application.properties}): a timeout, one retry on
 * timeouts and connection failures ({@code ProcessingException} of the model client) only, a
 * bulkhead of the executor's concurrency and a circuit breaker. The output guardrail allows one
 * reprompt.
 */
@RegisterAiService(modelName = "triage")
@ApplicationScoped
public interface TriageAssistant {

  @SystemMessage(fromResource = "prompts/triage/system.txt")
  @UserMessage(fromResource = "prompts/triage/user.txt")
  @OutputGuardrails(TriageOutputGuardrail.class)
  @Timeout(value = 30, unit = ChronoUnit.SECONDS)
  @Retry(
      maxRetries = 1,
      retryOn = {TimeoutException.class, ProcessingException.class})
  @Bulkhead(2)
  @CircuitBreaker(requestVolumeThreshold = 8, failureRatio = 0.5, delay = 60_000)
  Result<TriageResult> triage(
      String alertName,
      String source,
      String service,
      String severity,
      String summary,
      String description,
      String labels);
}
