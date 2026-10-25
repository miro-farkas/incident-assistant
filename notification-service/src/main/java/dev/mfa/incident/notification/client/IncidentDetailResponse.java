package dev.mfa.incident.notification.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.Map;

/**
 * The part of the incident detail (A-0006) that messages use; other fields are ignored.
 *
 * @param alert the alert as received (alert-v1); its values are untrusted text
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record IncidentDetailResponse(Map<String, Object> alert) {}
