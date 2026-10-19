package dev.mfa.incident.messaging;

import dev.mfa.incident.service.OutboxRelayService;
import io.quarkus.scheduler.Scheduled;
import io.smallrye.common.annotation.RunOnVirtualThread;
import jakarta.enterprise.context.ApplicationScoped;
import org.jboss.logging.Logger;

/** Triggers the outbox relay on every instance; the relay itself admits one at a time (P-0003). */
@ApplicationScoped
public class OutboxRelayJob {

  private static final Logger LOG = Logger.getLogger(OutboxRelayJob.class);

  private final OutboxRelayService relay;

  public OutboxRelayJob(OutboxRelayService relay) {
    this.relay = relay;
  }

  @Scheduled(
      identity = "outbox-relay",
      every = "${incident.outbox.relay.interval}",
      concurrentExecution = Scheduled.ConcurrentExecution.SKIP,
      skipExecutionIf = Scheduled.ApplicationNotRunning.class)
  @RunOnVirtualThread
  void relay() {
    int published = relay.relayPending();
    if (published > 0) {
      LOG.debugf("Published %d outbox events", published);
    }
  }
}
