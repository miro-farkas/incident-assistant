package dev.mfa.incident.notification.service;

import dev.mfa.incident.notification.client.IncidentApiClient;
import dev.mfa.incident.notification.client.IncidentDetailResponse;
import dev.mfa.incident.notification.dto.IncidentDetailsDto;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.eclipse.microprofile.faulttolerance.CircuitBreaker;
import org.eclipse.microprofile.faulttolerance.Fallback;
import org.eclipse.microprofile.faulttolerance.Retry;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.jboss.logging.Logger;

/**
 * Reads what a message shows from the incident API (A-0011). Calls have the REST client's timeouts
 * and are retried; a circuit breaker stops calling an API that keeps failing. When the details stay
 * unavailable the result is empty, and the caller sends the minimal message (P-0020). Limits in
 * {@code application.properties}.
 */
@ApplicationScoped
public class IncidentDetailsService {

  private static final Logger LOG = Logger.getLogger(IncidentDetailsService.class);

  private final IncidentApiClient api;

  public IncidentDetailsService(@RestClient IncidentApiClient api) {
    this.api = api;
  }

  @Retry
  @CircuitBreaker
  @Fallback(fallbackMethod = "unavailable")
  public Optional<IncidentDetailsDto> lookup(UUID incidentId) {
    IncidentDetailResponse detail = api.get(incidentId);
    Map<String, Object> alert = detail.alert() == null ? Map.of() : detail.alert();
    return Optional.of(
        new IncidentDetailsDto(
            text(alert.get("alertName")), text(alert.get("service")), text(alert.get("severity"))));
  }

  Optional<IncidentDetailsDto> unavailable(UUID incidentId, Throwable failure) {
    LOG.warnf(
        "Details of incident %s unavailable (%s); sending the minimal message",
        incidentId, failure.getClass().getSimpleName());
    return Optional.empty();
  }

  private static String text(Object value) {
    return value instanceof String s && !s.isBlank() ? s : null;
  }
}
