package dev.mfa.incident.dto;

import java.util.List;

/**
 * One page of incidents, newest first.
 *
 * @param page zero-based index of this page
 * @param size requested page size
 */
public record IncidentPageDto(
    List<IncidentDto> items, int page, int size, long totalItems, int totalPages) {}
