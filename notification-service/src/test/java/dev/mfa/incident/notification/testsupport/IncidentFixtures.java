package dev.mfa.incident.notification.testsupport;

import dev.mfa.incident.notification.client.IncidentDetailResponse;
import java.util.Map;

/** Incident details as the incident API returns them. */
public final class IncidentFixtures {

  /** Alert text that must never reach a message (A-0011). */
  public static final String SUMMARY = "Customers see errors at checkout";

  private IncidentFixtures() {}

  public static IncidentDetailResponse detail(String alertName, String service, String severity) {
    return new IncidentDetailResponse(
        Map.of(
            "source", "prometheus",
            "fingerprint", "fp-checkout",
            "alertName", alertName,
            "service", service,
            "severity", severity,
            "summary", SUMMARY,
            "description", "Error rate above 5% for 10 minutes"));
  }
}
