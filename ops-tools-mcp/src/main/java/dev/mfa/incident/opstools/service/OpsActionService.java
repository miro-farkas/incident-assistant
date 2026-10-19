package dev.mfa.incident.opstools.service;

import dev.mfa.incident.opstools.dto.ActionResult;
import dev.mfa.incident.opstools.dto.ActionStatus;
import dev.mfa.incident.opstools.dto.ActionType;
import dev.mfa.incident.opstools.service.ServiceProfile.Release;
import jakarta.enterprise.context.ApplicationScoped;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;
import org.jboss.logging.Logger;

/**
 * Executes the action tools idempotently (P-0006, A-0005). The mock records what it did but does
 * not change the simulated timeline, so read tools keep answering from the configured scenario.
 */
@ApplicationScoped
public class OpsActionService {

  private static final Logger LOG = Logger.getLogger(OpsActionService.class);

  private final ServiceCatalog catalog;
  private final IdempotencyStore store;
  private final Clock clock;

  public OpsActionService(ServiceCatalog catalog, IdempotencyStore store, Clock clock) {
    this.catalog = catalog;
    this.store = store;
    this.clock = clock;
  }

  /** Restarts all pods of the service one at a time. */
  public ActionResult restart(String service, String idempotencyKey) {
    return execute(
        ActionType.RESTART_SERVICE,
        service,
        "",
        idempotencyKey,
        profile ->
            new Outcome(
                ActionStatus.COMPLETED,
                "Restarted all %d pods of %s one at a time"
                    .formatted(profile.replicas(), profile.name())));
  }

  /**
   * Rolls the service back to {@code targetVersion}, or to the release before the running one when
   * it is null. Rejected when the target is not an earlier release of the service.
   */
  public ActionResult rollback(String service, String targetVersion, String idempotencyKey) {
    return execute(
        ActionType.ROLLBACK_DEPLOYMENT,
        service,
        targetVersion == null ? "" : targetVersion,
        idempotencyKey,
        profile -> rollbackOutcome(profile, targetVersion));
  }

  /** Scales the service to {@code replicas}; rejected above the service's maximum. */
  public ActionResult scale(String service, int replicas, String idempotencyKey) {
    return execute(
        ActionType.SCALE_SERVICE,
        service,
        Integer.toString(replicas),
        idempotencyKey,
        profile -> scaleOutcome(profile, replicas));
  }

  private ActionResult execute(
      ActionType action,
      String service,
      String parameters,
      String idempotencyKey,
      Function<ServiceProfile, Outcome> outcome) {
    String fingerprint = action + "|" + service + "|" + parameters;
    ActionResult result =
        store.execute(
            idempotencyKey,
            fingerprint,
            () -> {
              Outcome done = outcome.apply(catalog.access(service).profile());
              return new ActionResult(
                  actionId(idempotencyKey),
                  action,
                  service,
                  done.status(),
                  done.message(),
                  idempotencyKey,
                  clock.instant(),
                  false);
            });
    LOG.infof(
        "Action %s on %s: %s%s (action id %s)",
        action, service, result.status(), result.replayed() ? ", replayed" : "", result.actionId());
    return result;
  }

  private static Outcome rollbackOutcome(ServiceProfile profile, String targetVersion) {
    List<Release> earlier = profile.releases().subList(0, profile.releases().size() - 1);
    String running = profile.runningRelease().version();
    if (targetVersion == null) {
      String previous = earlier.getLast().version();
      return rolledBack(profile, running, previous);
    }
    boolean known = earlier.stream().anyMatch(release -> release.version().equals(targetVersion));
    if (!known) {
      return new Outcome(
          ActionStatus.REJECTED,
          "Version %s is not an earlier release of %s; earlier releases: %s"
              .formatted(
                  targetVersion,
                  profile.name(),
                  String.join(", ", earlier.stream().map(Release::version).toList())));
    }
    return rolledBack(profile, running, targetVersion);
  }

  private static Outcome rolledBack(ServiceProfile profile, String from, String to) {
    return new Outcome(
        ActionStatus.COMPLETED, "Rolled back %s from %s to %s".formatted(profile.name(), from, to));
  }

  private static Outcome scaleOutcome(ServiceProfile profile, int replicas) {
    if (replicas > profile.maxReplicas()) {
      return new Outcome(
          ActionStatus.REJECTED,
          "%s cannot run more than %d replicas".formatted(profile.name(), profile.maxReplicas()));
    }
    if (replicas == profile.replicas()) {
      return new Outcome(
          ActionStatus.COMPLETED,
          "%s already runs %d replicas".formatted(profile.name(), replicas));
    }
    return new Outcome(
        ActionStatus.COMPLETED,
        "Scaled %s from %d to %d replicas".formatted(profile.name(), profile.replicas(), replicas));
  }

  private static String actionId(String idempotencyKey) {
    return UUID.nameUUIDFromBytes(idempotencyKey.getBytes(StandardCharsets.UTF_8)).toString();
  }

  private record Outcome(ActionStatus status, String message) {}
}
