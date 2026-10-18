package dev.mfa.incident.health;

import dev.mfa.incident.config.InstanceConfig;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.health.HealthCheck;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.eclipse.microprofile.health.Liveness;

/** Reports which instance answered a health probe. */
@Liveness
@ApplicationScoped
public class InstanceHealthCheck implements HealthCheck {

  static final String NAME = "instance";
  static final String ID_KEY = "id";

  private final InstanceConfig config;

  public InstanceHealthCheck(InstanceConfig config) {
    this.config = config;
  }

  @Override
  public HealthCheckResponse call() {
    return HealthCheckResponse.named(NAME).withData(ID_KEY, config.id()).up().build();
  }
}
