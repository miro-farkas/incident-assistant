package dev.mfa.incident.opstools.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.mfa.incident.opstools.config.Scenario;
import dev.mfa.incident.opstools.exception.BackendUnavailableException;
import dev.mfa.incident.opstools.exception.UnknownServiceException;
import dev.mfa.incident.opstools.testsupport.TestConfig;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ServiceCatalogTest {

  @Test
  void rejectsConfigurationOfUnknownServices() {
    assertThatThrownBy(
            () ->
                new ServiceCatalog(
                    TestConfig.with(
                        Map.of(
                            "ops.services.shipping.scenario", "healthy",
                            "ops.services.checkout.scenario", "healthy"))))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage(
            "ops.services configures unknown services [shipping]; known services: checkout,"
                + " payments, inventory");
  }

  @Test
  void unconfiguredServiceIsHealthy() {
    ServiceCatalog catalog = new ServiceCatalog(TestConfig.with(Map.of()));

    assertThat(catalog.access("inventory").scenario()).isEqualTo(Scenario.HEALTHY);
  }

  @Test
  void accessAppliesTheConfiguredScenarioAndFault() {
    ServiceCatalog catalog =
        new ServiceCatalog(
            TestConfig.with(
                Map.of(
                    "ops.services.checkout.scenario", "memory-leak",
                    "ops.services.payments.fault", "unavailable")));

    assertThat(catalog.access("checkout").scenario()).isEqualTo(Scenario.MEMORY_LEAK);
    assertThatThrownBy(() -> catalog.access("payments"))
        .isInstanceOf(BackendUnavailableException.class);
    assertThatThrownBy(() -> catalog.access("Checkout"))
        .isInstanceOf(UnknownServiceException.class);
  }
}
