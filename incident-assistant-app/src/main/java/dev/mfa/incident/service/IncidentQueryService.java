package dev.mfa.incident.service;

import dev.mfa.incident.dto.ApprovalDecisionDto;
import dev.mfa.incident.dto.IncidentDetailDto;
import dev.mfa.incident.dto.IncidentPageDto;
import dev.mfa.incident.dto.IncidentSummaryDto;
import dev.mfa.incident.dto.StepDto;
import dev.mfa.incident.exception.IncidentNotFoundException;
import dev.mfa.incident.exception.UnknownIncidentStateException;
import dev.mfa.incident.mapper.ApprovalDecisionMapper;
import dev.mfa.incident.mapper.IncidentMapper;
import dev.mfa.incident.mapper.IncidentStepMapper;
import dev.mfa.incident.model.Incident;
import dev.mfa.incident.model.IncidentState;
import dev.mfa.incident.repository.AlertOccurrenceRepository;
import dev.mfa.incident.repository.ApprovalDecisionRepository;
import dev.mfa.incident.repository.IncidentRepository;
import dev.mfa.incident.repository.IncidentStepRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Read access to incidents for the approval API (A-0006). */
@ApplicationScoped
public class IncidentQueryService {

  /** Largest page the list accepts (A-0006). */
  public static final int MAX_PAGE_SIZE = 100;

  private final IncidentRepository incidents;
  private final IncidentStepRepository steps;
  private final AlertOccurrenceRepository occurrences;
  private final ApprovalDecisionRepository decisions;
  private final IncidentMapper incidentMapper;
  private final IncidentStepMapper stepMapper;
  private final ApprovalDecisionMapper decisionMapper;

  public IncidentQueryService(
      IncidentRepository incidents,
      IncidentStepRepository steps,
      AlertOccurrenceRepository occurrences,
      ApprovalDecisionRepository decisions,
      IncidentMapper incidentMapper,
      IncidentStepMapper stepMapper,
      ApprovalDecisionMapper decisionMapper) {
    this.incidents = incidents;
    this.steps = steps;
    this.occurrences = occurrences;
    this.decisions = decisions;
    this.incidentMapper = incidentMapper;
    this.stepMapper = stepMapper;
    this.decisionMapper = decisionMapper;
  }

  /**
   * Returns one page of incidents, newest first.
   *
   * @param stateNames names of the states to include; all states when empty
   * @param page zero-based page index
   * @param size page size, 1 to {@link #MAX_PAGE_SIZE}
   * @throws UnknownIncidentStateException if a name is not an {@link IncidentState}
   */
  @Transactional
  public IncidentPageDto list(Collection<String> stateNames, int page, int size) {
    Set<IncidentState> states = parseStates(stateNames);
    if (page < 0) {
      throw new IllegalArgumentException("page must not be negative");
    }
    if (size < 1 || size > MAX_PAGE_SIZE) {
      throw new IllegalArgumentException("size must be between 1 and " + MAX_PAGE_SIZE);
    }
    long total = incidents.countByStates(states);
    List<Incident> found = incidents.listByStates(states, page, size);
    Map<UUID, Long> occurrenceCounts =
        occurrences.countByIncidents(found.stream().map(Incident::getId).toList());
    List<IncidentSummaryDto> items =
        found.stream()
            .map(
                incident ->
                    incidentMapper.toSummaryDto(
                        incident, occurrenceCounts.getOrDefault(incident.getId(), 0L)))
            .toList();
    int totalPages = Math.toIntExact((total + size - 1) / size);
    return new IncidentPageDto(items, page, size, total, totalPages);
  }

  /**
   * @throws IncidentNotFoundException if no incident has this id
   */
  @Transactional
  public IncidentDetailDto detail(UUID incidentId) {
    Incident incident =
        incidents
            .findByIdOptional(incidentId)
            .orElseThrow(() -> new IncidentNotFoundException(incidentId));
    List<StepDto> history =
        steps.listByIncident(incidentId).stream().map(stepMapper::toDto).toList();
    ApprovalDecisionDto decision =
        decisions.findLatestByIncident(incidentId).map(decisionMapper::toDto).orElse(null);
    return incidentMapper.toDetailDto(
        incident, occurrences.countByIncident(incidentId), history, decision);
  }

  private static Set<IncidentState> parseStates(Collection<String> names) {
    Set<IncidentState> states = EnumSet.noneOf(IncidentState.class);
    for (String name : names) {
      try {
        states.add(IncidentState.valueOf(name));
      } catch (IllegalArgumentException e) {
        throw new UnknownIncidentStateException();
      }
    }
    return states;
  }
}
