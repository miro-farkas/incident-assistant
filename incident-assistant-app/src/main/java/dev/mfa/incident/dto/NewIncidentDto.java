package dev.mfa.incident.dto;

import static dev.mfa.incident.dto.Validation.requireText;

/**
 * Data needed to open an incident.
 *
 * @param source system that raised the alert
 * @param fingerprint alert identity within the source; one open incident per source and fingerprint
 *     (ADR-0004)
 * @param alertPayload the alert as a JSON document
 */
public record NewIncidentDto(String source, String fingerprint, String alertPayload) {

  public static final int MAX_SOURCE_LENGTH = 100;
  public static final int MAX_FINGERPRINT_LENGTH = 200;

  public NewIncidentDto {
    requireText(source, "source", MAX_SOURCE_LENGTH);
    requireText(fingerprint, "fingerprint", MAX_FINGERPRINT_LENGTH);
    requireText(alertPayload, "alertPayload");
  }
}
