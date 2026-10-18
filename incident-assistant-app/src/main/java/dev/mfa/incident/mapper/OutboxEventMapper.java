package dev.mfa.incident.mapper;

import dev.mfa.incident.dto.OutboxMessage;
import dev.mfa.incident.model.OutboxEvent;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class OutboxEventMapper {

  /**
   * @param payload the serialized message value
   */
  public OutboxEvent toEntity(OutboxMessage message, String payload) {
    OutboxEvent event = new OutboxEvent();
    event.setEventId(message.eventId());
    event.setMessageKey(message.messageKey());
    event.setEventType(message.eventType());
    event.setPayload(payload);
    return event;
  }
}
