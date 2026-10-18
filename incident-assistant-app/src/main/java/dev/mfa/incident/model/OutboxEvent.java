package dev.mfa.incident.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.Generated;

/** An event waiting in the transactional outbox (P-0003, P-0012). */
@Entity
@Table(name = "outbox_event")
public class OutboxEvent extends BaseEntity {

  /** Database identity; orders events for the relay. Read-only for the application. */
  @Generated
  @Column(name = "sequence_number", insertable = false, updatable = false)
  private Long sequenceNumber;

  @Column(name = "event_id", nullable = false, updatable = false)
  private UUID eventId;

  @Column(name = "message_key", nullable = false, updatable = false, length = 100)
  private String messageKey;

  @Column(name = "event_type", nullable = false, updatable = false, length = 100)
  private String eventType;

  /** Message value, published byte for byte; therefore {@code text}, not {@code jsonb}. */
  @Column(name = "payload", nullable = false, updatable = false, columnDefinition = "text")
  private String payload;

  @Column(name = "published_at")
  private Instant publishedAt;

  public Long getSequenceNumber() {
    return sequenceNumber;
  }

  public UUID getEventId() {
    return eventId;
  }

  public void setEventId(UUID eventId) {
    this.eventId = eventId;
  }

  public String getMessageKey() {
    return messageKey;
  }

  public void setMessageKey(String messageKey) {
    this.messageKey = messageKey;
  }

  public String getEventType() {
    return eventType;
  }

  public void setEventType(String eventType) {
    this.eventType = eventType;
  }

  public String getPayload() {
    return payload;
  }

  public void setPayload(String payload) {
    this.payload = payload;
  }

  /** Set by the outbox relay (INC-003); {@code null} while the event is pending. */
  public Instant getPublishedAt() {
    return publishedAt;
  }
}
