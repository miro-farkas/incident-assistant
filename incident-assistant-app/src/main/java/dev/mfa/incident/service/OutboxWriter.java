package dev.mfa.incident.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.mfa.incident.dto.OutboxMessage;
import dev.mfa.incident.mapper.OutboxEventMapper;
import dev.mfa.incident.repository.OutboxEventRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

/**
 * Writes messages to {@code outbox_event} inside the transaction of the state change that caused
 * them, so a message exists if and only if that change was committed (ADR-0003).
 */
@ApplicationScoped
public class OutboxWriter {

  private final OutboxEventRepository repository;
  private final OutboxEventMapper mapper;
  private final ObjectMapper objectMapper;

  public OutboxWriter(
      OutboxEventRepository repository, OutboxEventMapper mapper, ObjectMapper objectMapper) {
    this.repository = repository;
    this.mapper = mapper;
    this.objectMapper = objectMapper;
  }

  @Transactional(Transactional.TxType.MANDATORY)
  public void write(OutboxMessage message) {
    repository.persist(mapper.toEntity(message, serialize(message)));
  }

  private String serialize(OutboxMessage message) {
    try {
      return objectMapper.writeValueAsString(message);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("Cannot serialize message " + message.eventType(), e);
    }
  }
}
