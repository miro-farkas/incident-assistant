package dev.mfa.incident.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.OptimisticLock;
import org.hibernate.type.SqlTypes;

/** An incident and its current workflow state. Changed only by the service layer. */
@Entity
@Table(name = "incident")
public class Incident extends BaseEntity {

  @Column(name = "source", nullable = false, updatable = false, length = 100)
  private String source;

  @Column(name = "fingerprint", nullable = false, updatable = false, length = 200)
  private String fingerprint;

  @Enumerated(EnumType.STRING)
  @Column(name = "state", nullable = false, length = 32)
  private IncidentState state;

  /** The alert as JSON. Stored as {@code jsonb}, which normalises key order and whitespace. */
  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "alert_payload", nullable = false, updatable = false)
  private String alertPayload;

  /** Instance working on the incident (P-0003); {@code null} when nobody does. */
  @OptimisticLock(excluded = true)
  @Column(name = "lease_owner", length = 64)
  private String leaseOwner;

  /** End of the lease; afterwards any instance may take the incident. */
  @OptimisticLock(excluded = true)
  @Column(name = "lease_expires_at")
  private Instant leaseExpiresAt;

  public String getSource() {
    return source;
  }

  public void setSource(String source) {
    this.source = source;
  }

  public String getFingerprint() {
    return fingerprint;
  }

  public void setFingerprint(String fingerprint) {
    this.fingerprint = fingerprint;
  }

  public IncidentState getState() {
    return state;
  }

  public void setState(IncidentState state) {
    this.state = state;
  }

  public String getAlertPayload() {
    return alertPayload;
  }

  public void setAlertPayload(String alertPayload) {
    this.alertPayload = alertPayload;
  }

  public String getLeaseOwner() {
    return leaseOwner;
  }

  public void setLeaseOwner(String leaseOwner) {
    this.leaseOwner = leaseOwner;
  }

  public Instant getLeaseExpiresAt() {
    return leaseExpiresAt;
  }

  public void setLeaseExpiresAt(Instant leaseExpiresAt) {
    this.leaseExpiresAt = leaseExpiresAt;
  }
}
