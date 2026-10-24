package dev.mfa.incident.notification.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * One message for one recipient, caused by one event and rule (P-0020). The event fields the
 * message needs are copied, so sending does not depend on the event again.
 */
@Entity
@Table(name = "notification")
public class Notification extends BaseEntity {

  @Column(name = "event_id", nullable = false, updatable = false)
  private UUID eventId;

  @Column(name = "incident_id", nullable = false, updatable = false)
  private UUID incidentId;

  /** Name of the rule that matched, which also selects the message templates. */
  @Column(name = "rule", nullable = false, updatable = false, length = 64)
  private String rule;

  @Column(name = "state", nullable = false, updatable = false, length = 32)
  private String state;

  @Column(name = "reason_code", updatable = false, length = 64)
  private String reasonCode;

  @Column(name = "occurred_at", nullable = false, updatable = false)
  private Instant occurredAt;

  @Enumerated(EnumType.STRING)
  @Column(name = "channel", nullable = false, updatable = false, length = 16)
  private ChannelType channel;

  @Column(name = "recipient", nullable = false, updatable = false, length = 320)
  private String recipient;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 16)
  private NotificationStatus status;

  @Column(name = "attempts", nullable = false)
  private int attempts;

  @Column(name = "next_attempt_at", nullable = false)
  private Instant nextAttemptAt;

  @Column(name = "last_error", length = 1000)
  private String lastError;

  @Column(name = "sent_at")
  private Instant sentAt;

  public UUID getEventId() {
    return eventId;
  }

  public void setEventId(UUID eventId) {
    this.eventId = eventId;
  }

  public UUID getIncidentId() {
    return incidentId;
  }

  public void setIncidentId(UUID incidentId) {
    this.incidentId = incidentId;
  }

  public String getRule() {
    return rule;
  }

  public void setRule(String rule) {
    this.rule = rule;
  }

  public String getState() {
    return state;
  }

  public void setState(String state) {
    this.state = state;
  }

  public String getReasonCode() {
    return reasonCode;
  }

  public void setReasonCode(String reasonCode) {
    this.reasonCode = reasonCode;
  }

  public Instant getOccurredAt() {
    return occurredAt;
  }

  public void setOccurredAt(Instant occurredAt) {
    this.occurredAt = occurredAt;
  }

  public ChannelType getChannel() {
    return channel;
  }

  public void setChannel(ChannelType channel) {
    this.channel = channel;
  }

  public String getRecipient() {
    return recipient;
  }

  public void setRecipient(String recipient) {
    this.recipient = recipient;
  }

  public NotificationStatus getStatus() {
    return status;
  }

  public void setStatus(NotificationStatus status) {
    this.status = status;
  }

  public int getAttempts() {
    return attempts;
  }

  public void setAttempts(int attempts) {
    this.attempts = attempts;
  }

  public Instant getNextAttemptAt() {
    return nextAttemptAt;
  }

  public void setNextAttemptAt(Instant nextAttemptAt) {
    this.nextAttemptAt = nextAttemptAt;
  }

  public String getLastError() {
    return lastError;
  }

  public void setLastError(String lastError) {
    this.lastError = lastError;
  }

  public Instant getSentAt() {
    return sentAt;
  }

  public void setSentAt(Instant sentAt) {
    this.sentAt = sentAt;
  }
}
