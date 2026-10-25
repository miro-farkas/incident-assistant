package dev.mfa.incident.service;

import dev.mfa.incident.config.WorkflowConfig;
import dev.mfa.incident.dto.ClaimedIncidentDto;
import dev.mfa.incident.model.IncidentState;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import org.jboss.logging.Logger;

/**
 * Advances incidents through their explicit steps (P-0003, A-0014). Each run claims as many
 * incidents as it has free slots ({@code incident.workflow.executor.max-concurrency}), only in
 * states that have a {@link WorkflowStep}, and runs each step on a virtual thread of its own. An
 * unexpected failure leaves the lease to expire, so the incident is claimed again later.
 */
@ApplicationScoped
public class StepExecutor {

  private static final Logger LOG = Logger.getLogger(StepExecutor.class);

  private final IncidentLeaseService leases;
  private final Map<IncidentState, WorkflowStep> steps = new EnumMap<>(IncidentState.class);
  private final Semaphore slots;
  private final ExecutorService threads = Executors.newVirtualThreadPerTaskExecutor();

  public StepExecutor(
      IncidentLeaseService leases, Instance<WorkflowStep> steps, WorkflowConfig config) {
    this.leases = leases;
    steps.forEach(
        step -> {
          if (this.steps.put(step.sourceState(), step) != null) {
            throw new IllegalStateException("Two steps run in state " + step.sourceState());
          }
        });
    this.slots = new Semaphore(config.executor().maxConcurrency());
  }

  /**
   * Claims due incidents and starts their steps; returns without waiting for them.
   *
   * @return steps started
   */
  public int runDue() {
    int free = slots.availablePermits();
    if (free == 0) {
      return 0;
    }
    List<ClaimedIncidentDto> claimed = leases.claim(steps.keySet(), free);
    for (ClaimedIncidentDto incident : claimed) {
      slots.acquireUninterruptibly();
      threads.submit(() -> runStep(incident));
    }
    return claimed.size();
  }

  private void runStep(ClaimedIncidentDto incident) {
    try {
      steps.get(incident.state()).run(incident);
    } catch (RuntimeException e) {
      LOG.errorf(
          e,
          "Step of incident %s failed; its lease expires and it is claimed again",
          incident.id());
    } finally {
      slots.release();
    }
  }

  @PreDestroy
  void stop() {
    threads.shutdown();
  }
}
