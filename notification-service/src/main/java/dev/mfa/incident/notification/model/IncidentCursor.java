package dev.mfa.incident.notification.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.UUID;

/** The newest version of an incident seen in an event (P-0020). */
@Entity
@Table(name = "incident_cursor")
public class IncidentCursor extends BaseEntity {

  @Column(name = "incident_id", nullable = false, updatable = false)
  private UUID incidentId;

  @Column(name = "last_version", nullable = false)
  private long lastVersion;

  public UUID getIncidentId() {
    return incidentId;
  }

  public void setIncidentId(UUID incidentId) {
    this.incidentId = incidentId;
  }

  public long getLastVersion() {
    return lastVersion;
  }

  public void setLastVersion(long lastVersion) {
    this.lastVersion = lastVersion;
  }
}
