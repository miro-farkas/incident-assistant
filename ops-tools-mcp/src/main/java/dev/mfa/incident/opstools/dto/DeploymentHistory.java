package dev.mfa.incident.opstools.dto;

import java.util.List;

/**
 * Recent deployments of a service, newest first; the first one is the running version.
 *
 * @param truncated whether older deployments exist than returned
 */
public record DeploymentHistory(String service, List<Deployment> deployments, boolean truncated) {

  public DeploymentHistory {
    deployments = List.copyOf(deployments);
  }
}
