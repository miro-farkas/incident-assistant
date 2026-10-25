package dev.mfa.incident.notification.dto;

/**
 * What a message shows from the incident detail (A-0011). Until the typed triage result exists
 * (INC-006), service and severity are the ones the alert reported. Every value is text from an
 * alert and therefore untrusted; {@code null} when the alert has none.
 */
public record IncidentDetailsDto(String alertName, String service, String severity) {}
