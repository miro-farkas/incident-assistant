package dev.mfa.incident.service;

import dev.mfa.incident.config.OutboxRelayConfig;
import dev.mfa.incident.exception.OutboxPublishException;
import dev.mfa.incident.mapper.OutboxEventMapper;
import dev.mfa.incident.model.OutboxEvent;
import dev.mfa.incident.repository.OutboxEventRepository;
import io.quarkus.narayana.jta.QuarkusTransaction;
import jakarta.enterprise.context.ApplicationScoped;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.jboss.logging.Logger;

/**
 * Publishes outbox events in {@code sequence_number} order (P-0003). One relay runs at a time
 * across all instances, admitted by a transaction-scoped advisory lock, and it sends one event at a
 * time and waits for the acknowledgement, so the events of one incident reach the topic in the
 * order they were written. An event is marked published only after the broker acknowledged it.
 * Delivery is at least once: if marking fails after the send, the event is sent again.
 */
@ApplicationScoped
public class OutboxRelayService {

  private static final Logger LOG = Logger.getLogger(OutboxRelayService.class);

  /** Time a batch transaction allows after its last send for marking events and committing. */
  private static final Duration COMMIT_ALLOWANCE = Duration.ofSeconds(10);

  private final OutboxEventRepository events;
  private final OutboxEventMapper mapper;
  private final OutboxPublisher publisher;
  private final OutboxRelayConfig config;
  private final int batchTimeoutSeconds;

  public OutboxRelayService(
      OutboxEventRepository events,
      OutboxEventMapper mapper,
      OutboxPublisher publisher,
      OutboxRelayConfig config) {
    this.events = events;
    this.mapper = mapper;
    this.publisher = publisher;
    this.config = config;
    this.batchTimeoutSeconds =
        Math.toIntExact(
            config
                .maxBatchDuration()
                .plus(config.sendTimeout())
                .plus(COMMIT_ALLOWANCE)
                .toSeconds());
  }

  /**
   * Publishes pending events batch by batch until none is left, a send fails, or another instance
   * holds the relay lock.
   *
   * @return number of events published
   */
  public int relayPending() {
    int published = 0;
    BatchResult batch;
    do {
      batch = QuarkusTransaction.requiringNew().timeout(batchTimeoutSeconds).call(this::relayBatch);
      published += batch.published();
    } while (batch.morePending());
    return published;
  }

  private BatchResult relayBatch() {
    if (!events.tryLockRelay()) {
      return new BatchResult(0, false);
    }
    List<OutboxEvent> pending = events.listUnpublished(config.batchSize());
    long deadline = System.nanoTime() + config.maxBatchDuration().toNanos();
    int published = 0;
    for (OutboxEvent event : pending) {
      if (System.nanoTime() - deadline > 0) {
        return new BatchResult(published, true);
      }
      try {
        publisher.publish(mapper.toRecord(event));
      } catch (OutboxPublishException e) {
        LOG.warnf(
            "Outbox relay stopped at sequence %d, retrying next run: %s",
            event.getSequenceNumber(), e.getMessage());
        return new BatchResult(published, false);
      }
      event.setPublishedAt(Instant.now());
      published++;
    }
    return new BatchResult(published, pending.size() == config.batchSize());
  }

  private record BatchResult(int published, boolean morePending) {}
}
