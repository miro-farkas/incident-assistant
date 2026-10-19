package dev.mfa.incident.opstools.service;

import static java.time.Duration.ofDays;

import dev.mfa.incident.opstools.config.OpsToolsConfig;
import dev.mfa.incident.opstools.config.OpsToolsConfig.ServiceSettings;
import dev.mfa.incident.opstools.exception.BackendUnavailableException;
import dev.mfa.incident.opstools.exception.UnknownServiceException;
import dev.mfa.incident.opstools.service.ServiceProfile.Release;
import io.quarkus.runtime.Startup;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.SequencedMap;
import org.jboss.logging.Logger;

/**
 * The mock services known to the ops backend and the scenario and fault configured for each
 * (A-0005). Fails startup when {@code ops.services} names a service that is not catalogued.
 */
@Startup
@ApplicationScoped
public class ServiceCatalog {

  private static final Logger LOG = Logger.getLogger(ServiceCatalog.class);

  private static final SequencedMap<String, ServiceProfile> PROFILES =
      catalogue(
          new ServiceProfile(
              "checkout",
              List.of("payments", "inventory"),
              3,
              10,
              120,
              List.of(
                  new Release("2.13.0", ofDays(30), "ci-pipeline", "Upgrade HTTP client"),
                  new Release("2.14.0", ofDays(21), "ci-pipeline", "Add gift card payments"),
                  new Release("2.14.1", ofDays(14), "ci-pipeline", "Fix rounding of tax lines"),
                  new Release("2.15.0", ofDays(7), "ci-pipeline", "Cache product prices"),
                  new Release(
                      "2.16.0", ofDays(2), "ci-pipeline", "Refactor discount calculation"))),
          new ServiceProfile(
              "payments",
              List.of("fraud-check", "bank-gateway"),
              4,
              12,
              80,
              List.of(
                  new Release("5.2.0", ofDays(40), "ci-pipeline", "Support 3-D Secure 2.2"),
                  new Release("5.3.0", ofDays(26), "ci-pipeline", "Add refund webhooks"),
                  new Release("5.3.1", ofDays(18), "ci-pipeline", "Retry bank timeouts once"),
                  new Release("5.4.0", ofDays(9), "ci-pipeline", "Switch fraud-check to v2"),
                  new Release("5.4.1", ofDays(3), "ci-pipeline", "Tune connection pool"))),
          new ServiceProfile(
              "inventory",
              List.of("inventory-db"),
              3,
              8,
              200,
              List.of(
                  new Release("1.7.0", ofDays(45), "ci-pipeline", "Reserve stock per order"),
                  new Release("1.7.1", ofDays(33), "ci-pipeline", "Fix stock sync race"),
                  new Release("1.8.0", ofDays(20), "ci-pipeline", "In-memory stock cache"),
                  new Release("1.8.1", ofDays(12), "ci-pipeline", "Log cache statistics"),
                  new Release("1.8.2", ofDays(6), "ci-pipeline", "Upgrade JDBC driver"))));

  private final OpsToolsConfig config;

  public ServiceCatalog(OpsToolsConfig config) {
    List<String> unknown =
        config.services().keySet().stream()
            .filter(name -> !PROFILES.containsKey(name))
            .sorted()
            .toList();
    if (!unknown.isEmpty()) {
      throw new IllegalStateException(
          "ops.services configures unknown services %s; known services: %s"
              .formatted(unknown, String.join(", ", PROFILES.keySet())));
    }
    this.config = config;
    PROFILES
        .keySet()
        .forEach(
            name -> {
              ServiceSettings settings = config.services().get(name);
              LOG.infof(
                  "Mock service %s simulates %s with fault %s",
                  name, settings.scenario(), settings.fault());
            });
  }

  /**
   * The service as the backend currently answers for it, after the configured fault took effect.
   *
   * @throws UnknownServiceException if the service is not catalogued
   * @throws BackendUnavailableException if the service is configured as unavailable, or the
   *     simulated delay was interrupted
   */
  SimulatedService access(String name) {
    ServiceProfile profile = PROFILES.get(name);
    if (profile == null) {
      throw new UnknownServiceException(name, PROFILES.keySet());
    }
    ServiceSettings settings = config.services().get(name);
    switch (settings.fault()) {
      case NONE -> {}
      case UNAVAILABLE -> throw new BackendUnavailableException(name);
      case SLOW -> delay(name);
    }
    return new SimulatedService(profile, settings.scenario());
  }

  private void delay(String name) {
    try {
      Thread.sleep(config.faults().slowDelay());
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new BackendUnavailableException(name);
    }
  }

  private static SequencedMap<String, ServiceProfile> catalogue(ServiceProfile... profiles) {
    SequencedMap<String, ServiceProfile> byName = new LinkedHashMap<>();
    for (ServiceProfile profile : profiles) {
      byName.put(profile.name(), profile);
    }
    return Collections.unmodifiableSequencedMap(byName);
  }
}
