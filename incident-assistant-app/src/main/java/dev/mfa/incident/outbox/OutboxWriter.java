package dev.mfa.incident.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.mfa.incident.outbox.persistence.OutboxEventEntity;
import dev.mfa.incident.outbox.persistence.OutboxEventRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import java.time.Clock;

/**
 * Writes events to {@code outbox_event} inside the transaction of the state change that caused
 * them, so an event exists if and only if that change was committed (ADR-0003).
 */
@ApplicationScoped
public class OutboxWriter {

  private final OutboxEventRepository repository;
  private final ObjectMapper objectMapper;
  private final Clock clock;

  public OutboxWriter(OutboxEventRepository repository, ObjectMapper objectMapper, Clock clock) {
    this.repository = repository;
    this.objectMapper = objectMapper;
    this.clock = clock;
  }

  @Transactional(Transactional.TxType.MANDATORY)
  public void write(OutboxEvent event) {
    repository.persist(
        new OutboxEventEntity(
            event.eventId(),
            event.messageKey(),
            event.eventType(),
            serialize(event),
            clock.instant()));
  }

  private String serialize(OutboxEvent event) {
    try {
      return objectMapper.writeValueAsString(event);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("Cannot serialize event " + event.eventType(), e);
    }
  }
}
