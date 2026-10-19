package dev.mfa.incident.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * A repeated alert for an incident that was already open (A-0003). {@code createdAt} is the time it
 * was received. Rows are never updated: the repository only inserts them.
 */
@Entity
@Table(name = "alert_occurrence")
public class AlertOccurrence extends BaseEntity {

  @Column(name = "incident_id", nullable = false, updatable = false)
  private UUID incidentId;

  /** The alert as JSON. Stored as {@code jsonb}, which normalises key order and whitespace. */
  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "alert_payload", nullable = false, updatable = false)
  private String alertPayload;

  public UUID getIncidentId() {
    return incidentId;
  }

  public void setIncidentId(UUID incidentId) {
    this.incidentId = incidentId;
  }

  public String getAlertPayload() {
    return alertPayload;
  }

  public void setAlertPayload(String alertPayload) {
    this.alertPayload = alertPayload;
  }
}
