package dev.mfa.incident.config;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import java.time.Duration;
import org.hibernate.validator.constraints.time.DurationMin;

/** The step executor and the limits of one workflow (P-0003, P-0008, A-0014, A-0015). */
@ConfigMapping(prefix = "incident.workflow")
public interface WorkflowConfig {

  /**
   * Tokens all steps of one incident may use together; above it the incident is escalated. Tool
   * results are sent back to the model on every round trip of the diagnosis, hence the size.
   */
  @WithDefault("60000")
  @Min(1)
  long tokenBudget();

  /**
   * Provider and model of the triage step, for example {@code ollama/qwen2.5:3b}; recorded on the
   * step row when the model did not report its name, as after a failed call.
   */
  @NotBlank
  String triageModel();

  /** Provider and model of the diagnosis step, recorded like {@link #triageModel()}. */
  @NotBlank
  String diagnosisModel();

  Diagnosis diagnosis();

  Executor executor();

  interface Diagnosis {

    /**
     * Tool calls one diagnosis may make, across all tools; one more escalates the incident. The
     * prompt tells the model the number.
     */
    @WithDefault("6")
    @Min(1)
    @Max(20)
    int maxToolCalls();
  }

  interface Executor {

    /** Off in tests that drive the workflow themselves; on in every other profile. */
    @WithDefault("true")
    boolean enabled();

    /** Pause between runs that claim new work. */
    @WithDefault("2s")
    @DurationMin(millis = 1)
    Duration interval();

    /** Steps running at the same time on this instance; also the bulkhead of the LLM calls. */
    @WithDefault("2")
    @Min(1)
    @Max(64)
    int maxConcurrency();

    /**
     * How long a claimed incident stays reserved for this instance. Longer than one step with its
     * retries, so a lease only expires when the instance stopped working on it.
     */
    @WithDefault("5m")
    @DurationMin(seconds = 10)
    Duration leaseDuration();
  }
}
