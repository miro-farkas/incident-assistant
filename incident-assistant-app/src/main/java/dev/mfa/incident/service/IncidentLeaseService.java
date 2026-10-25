package dev.mfa.incident.service;

import dev.mfa.incident.config.InstanceConfig;
import dev.mfa.incident.config.WorkflowConfig;
import dev.mfa.incident.dto.ClaimedIncidentDto;
import dev.mfa.incident.mapper.IncidentMapper;
import dev.mfa.incident.model.Incident;
import dev.mfa.incident.model.IncidentState;
import dev.mfa.incident.repository.IncidentRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import java.time.Instant;
import java.util.List;
import java.util.Set;

/**
 * Work leases of the step executor (P-0003, A-0014). The lease columns are excluded from optimistic
 * locking, so a claim leaves the incident version unchanged; the step result is recorded against
 * that version and releases the lease ({@link IncidentService#recordStep}).
 */
@ApplicationScoped
public class IncidentLeaseService {

  private final IncidentRepository incidents;
  private final IncidentMapper mapper;
  private final String instanceId;
  private final WorkflowConfig.Executor config;

  public IncidentLeaseService(
      IncidentRepository incidents,
      IncidentMapper mapper,
      InstanceConfig instance,
      WorkflowConfig config) {
    this.incidents = incidents;
    this.mapper = mapper;
    this.instanceId = instance.id();
    this.config = config.executor();
  }

  /**
   * Claims up to {@code limit} incidents in {@code states} that nobody holds, oldest first, for
   * this instance and {@code lease-duration}. Concurrent claims on other instances get other
   * incidents ({@code SKIP LOCKED}).
   */
  @Transactional
  public List<ClaimedIncidentDto> claim(Set<IncidentState> states, int limit) {
    if (states.isEmpty() || limit < 1) {
      return List.of();
    }
    Instant now = Instant.now();
    List<Incident> claimed = incidents.lockClaimable(states, now, limit);
    for (Incident incident : claimed) {
      incident.setLeaseOwner(instanceId);
      incident.setLeaseExpiresAt(now.plus(config.leaseDuration()));
    }
    return claimed.stream().map(mapper::toClaimed).toList();
  }
}
