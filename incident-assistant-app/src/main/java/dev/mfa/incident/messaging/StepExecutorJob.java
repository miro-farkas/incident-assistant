package dev.mfa.incident.messaging;

import dev.mfa.incident.config.WorkflowConfig;
import dev.mfa.incident.service.StepExecutor;
import io.quarkus.scheduler.Scheduled;
import io.smallrye.common.annotation.RunOnVirtualThread;
import jakarta.enterprise.context.ApplicationScoped;
import org.jboss.logging.Logger;

/**
 * Starts due workflow steps on every instance at {@code incident.workflow.executor.interval};
 * instances claim disjoint incidents (P-0003, A-0014). Off with {@code
 * incident.workflow.executor.enabled=false}.
 */
@ApplicationScoped
public class StepExecutorJob {

  private static final Logger LOG = Logger.getLogger(StepExecutorJob.class);

  private final StepExecutor executor;
  private final boolean enabled;

  public StepExecutorJob(StepExecutor executor, WorkflowConfig config) {
    this.executor = executor;
    this.enabled = config.executor().enabled();
  }

  @Scheduled(
      identity = "step-executor",
      every = "${incident.workflow.executor.interval}",
      concurrentExecution = Scheduled.ConcurrentExecution.SKIP,
      skipExecutionIf = Scheduled.ApplicationNotRunning.class)
  @RunOnVirtualThread
  void runDue() {
    if (!enabled) {
      return;
    }
    int started = executor.runDue();
    if (started > 0) {
      LOG.debugf("Started %d workflow steps", started);
    }
  }
}
