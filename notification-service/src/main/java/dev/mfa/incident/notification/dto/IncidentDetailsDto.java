package dev.mfa.incident.notification.dto;

/**
 * What a message shows from the incident detail (A-0011): service and severity of the triage result
 * (A-0014) once the incident is triaged, before that the ones the alert reported. Every value is
 * text from an alert or a model and therefore untrusted; {@code null} when there is none.
 *
 * @param triaged whether service and severity come from the triage result
 */
public record IncidentDetailsDto(
    String alertName, String service, String severity, boolean triaged) {}
