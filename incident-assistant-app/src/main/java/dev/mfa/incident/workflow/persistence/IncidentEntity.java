package dev.mfa.incident.workflow.persistence;

import dev.mfa.incident.workflow.Incident;
import dev.mfa.incident.workflow.IncidentState;
import dev.mfa.incident.workflow.IncidentStateMachine;
import dev.mfa.incident.workflow.NewIncident;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UuidGenerator;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "incident")
public class IncidentEntity {

  @Id
  @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
  private UUID id;

  @Column(name = "source", nullable = false, updatable = false, length = 100)
  private String source;

  @Column(name = "fingerprint", nullable = false, updatable = false, length = 200)
  private String fingerprint;

  @Enumerated(EnumType.STRING)
  @Column(name = "state", nullable = false, length = 32)
  private IncidentState state;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "alert_payload", nullable = false, updatable = false)
  private String alertPayload;

  @Version
  @Column(name = "version", nullable = false)
  private long version;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  protected IncidentEntity() {}

  public static IncidentEntity received(NewIncident incident, Instant now) {
    IncidentEntity entity = new IncidentEntity();
    entity.source = incident.source();
    entity.fingerprint = incident.fingerprint();
    entity.alertPayload = incident.alertPayload();
    entity.state = IncidentStateMachine.INITIAL_STATE;
    entity.createdAt = now;
    entity.updatedAt = now;
    return entity;
  }

  /**
   * @throws dev.mfa.incident.workflow.IllegalStateTransitionException if the state machine does not
   *     allow the transition
   */
  public void transitionTo(IncidentState target, Instant now) {
    IncidentStateMachine.requireTransition(state, target);
    state = target;
    updatedAt = now;
  }

  public UUID id() {
    return id;
  }

  public IncidentState state() {
    return state;
  }

  public long version() {
    return version;
  }

  public Incident toDomain() {
    return new Incident(id, source, fingerprint, state, version, createdAt, updatedAt);
  }
}
