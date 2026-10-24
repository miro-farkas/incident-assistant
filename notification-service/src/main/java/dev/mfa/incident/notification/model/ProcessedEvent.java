package dev.mfa.incident.notification.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.UUID;

/** An incident event already handled (P-0020); {@code createdAt} is when. Append-only. */
@Entity
@Table(name = "processed_event")
public class ProcessedEvent extends BaseEntity {

  @Column(name = "event_id", nullable = false, updatable = false)
  private UUID eventId;

  @Column(name = "incident_id", nullable = false, updatable = false)
  private UUID incidentId;

  @Column(name = "incident_version", nullable = false, updatable = false)
  private long incidentVersion;

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

  public long getIncidentVersion() {
    return incidentVersion;
  }

  public void setIncidentVersion(long incidentVersion) {
    this.incidentVersion = incidentVersion;
  }
}
