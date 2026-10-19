package dev.mfa.incident.testsupport;

import dev.mfa.incident.dto.OutboxRecord;
import dev.mfa.incident.exception.OutboxPublishException;
import dev.mfa.incident.service.OutboxPublisher;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Alternative;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Records published outbox records instead of sending them, and fails on request. Not enabled by
 * default: test profiles opt in with {@code getEnabledAlternatives()}.
 */
@Alternative
@ApplicationScoped
public class RecordingOutboxPublisher implements OutboxPublisher {

  private final List<OutboxRecord> published = new CopyOnWriteArrayList<>();
  private final Set<UUID> failing = ConcurrentHashMap.newKeySet();

  @Override
  public void publish(OutboxRecord outboxRecord) {
    if (failing.contains(outboxRecord.eventId())) {
      throw new OutboxPublishException(outboxRecord.eventId(), "simulated broker failure", null);
    }
    published.add(outboxRecord);
  }

  /** Published records with this key, in publishing order. */
  public List<OutboxRecord> published(String key) {
    return published.stream().filter(r -> r.key().equals(key)).toList();
  }

  public void failOn(UUID eventId) {
    failing.add(eventId);
  }

  public void reset() {
    published.clear();
    failing.clear();
  }
}
