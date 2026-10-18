package dev.mfa.incident.outbox.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** Row of {@code outbox_event}. The identity id orders events for the relay. */
@Entity
@Table(name = "outbox_event")
public class OutboxEventEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "event_id", nullable = false, updatable = false)
  private UUID eventId;

  @Column(name = "message_key", nullable = false, updatable = false, length = 100)
  private String messageKey;

  @Column(name = "event_type", nullable = false, updatable = false, length = 100)
  private String eventType;

  @Column(name = "payload", nullable = false, updatable = false, columnDefinition = "text")
  private String payload;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "published_at")
  private Instant publishedAt;

  protected OutboxEventEntity() {}

  public OutboxEventEntity(
      UUID eventId, String messageKey, String eventType, String payload, Instant createdAt) {
    this.eventId = eventId;
    this.messageKey = messageKey;
    this.eventType = eventType;
    this.payload = payload;
    this.createdAt = createdAt;
  }

  public UUID eventId() {
    return eventId;
  }

  public String messageKey() {
    return messageKey;
  }

  public String eventType() {
    return eventType;
  }

  public String payload() {
    return payload;
  }

  public Instant publishedAt() {
    return publishedAt;
  }
}
