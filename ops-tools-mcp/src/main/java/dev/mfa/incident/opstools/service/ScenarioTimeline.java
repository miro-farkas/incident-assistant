package dev.mfa.incident.opstools.service;

import static java.time.temporal.ChronoUnit.MINUTES;

import dev.mfa.incident.opstools.config.Scenario;
import dev.mfa.incident.opstools.dto.DependencyHealth;
import dev.mfa.incident.opstools.dto.Deployment;
import dev.mfa.incident.opstools.dto.HealthStatus;
import dev.mfa.incident.opstools.dto.LogEntry;
import dev.mfa.incident.opstools.dto.LogLevel;
import dev.mfa.incident.opstools.dto.MetricPoint;
import dev.mfa.incident.opstools.dto.ServiceHealth;
import dev.mfa.incident.opstools.service.ServiceProfile.Release;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * The simulated history of one service up to the anchor time, computed from its profile and
 * scenario only (A-0005). Times are given as whole minutes before the anchor; the same inputs
 * always produce the same data.
 */
final class ScenarioTimeline {

  /** The bad release of {@link Scenario#BAD_DEPLOYMENT} was rolled out this long ago. */
  static final int BAD_DEPLOYMENT_MINUTES_AGO = 25;

  /** The dependency of {@link Scenario#DOWNSTREAM_TIMEOUT} stopped answering this long ago. */
  static final int TIMEOUT_ONSET_MINUTES_AGO = 15;

  /** Heap usage of {@link Scenario#MEMORY_LEAK} starts growing this long ago. */
  static final int LEAK_START_MINUTES_AGO = 180;

  /** Pods of {@link Scenario#MEMORY_LEAK} were killed out of memory these minutes ago. */
  static final List<Integer> OOM_KILLS_MINUTES_AGO = List.of(3, 11, 17);

  /** Client timeout of calls to a dependency. */
  static final long CLIENT_TIMEOUT_MS = 5000;

  private static final int OOM_PHASE_MINUTES_AGO = 20;
  private static final int SUMMARY_INTERVAL_MINUTES = 5;
  private static final int GC_WARNING_INTERVAL_MINUTES = 3;
  private static final int RETRY_WARNING_INTERVAL_MINUTES = 3;
  private static final int STACK_TRACE_INTERVAL_MINUTES = 2;
  private static final double GC_PRESSURE_MEMORY_PERCENT = 80;
  private static final double MAX_MEMORY_PERCENT = 97;
  private static final double BASE_MEMORY_PERCENT = 55;
  private static final double LEAK_PERCENT_PER_MINUTE = 0.25;
  private static final int LEAKING_POD = 1;

  private final ServiceProfile profile;
  private final Scenario scenario;
  private final Instant anchor;

  ScenarioTimeline(SimulatedService service, Instant anchor) {
    this.profile = service.profile();
    this.scenario = service.scenario();
    this.anchor = anchor;
  }

  Instant at(int minutesAgo) {
    return anchor.minus(minutesAgo, MINUTES);
  }

  MetricPoint metricsAt(int minutesAgo) {
    double jitter = jitter(minutesAgo);
    double cpu = 35 + 3 * jitter;
    double memory = BASE_MEMORY_PERCENT + 2 * jitter;
    double requestRate = profile.baseRequestsPerSecond() + 4 * jitter;
    double errorRate = 0.4 + 0.2 * jitter;
    double latency = 180 + 15 * jitter;
    switch (scenario) {
      case HEALTHY -> {}
      case BAD_DEPLOYMENT -> {
        if (minutesAgo < BAD_DEPLOYMENT_MINUTES_AGO) {
          cpu = 48 + 3 * jitter;
          errorRate = 12 + jitter;
          latency = 950 + 40 * jitter;
        }
      }
      case MEMORY_LEAK -> {
        memory = leakingMemory(minutesAgo);
        if (memory > GC_PRESSURE_MEMORY_PERCENT) {
          cpu += (memory - GC_PRESSURE_MEMORY_PERCENT) * 2;
          latency += (memory - GC_PRESSURE_MEMORY_PERCENT) * 35;
        }
        if (minutesAgo < OOM_PHASE_MINUTES_AGO) {
          errorRate = 3.5 + jitter;
        }
      }
      case DOWNSTREAM_TIMEOUT -> {
        if (minutesAgo < TIMEOUT_ONSET_MINUTES_AGO) {
          cpu = 20 + 2 * jitter;
          requestRate *= 0.85;
          errorRate = 8.5 + jitter;
          latency = CLIENT_TIMEOUT_MS;
        }
      }
    }
    return new MetricPoint(
        at(minutesAgo),
        round(cpu),
        round(memory),
        round(requestRate),
        round(Math.max(0, errorRate)),
        Math.round(latency));
  }

  /** All log entries of the last {@code minutes} minutes, newest first. */
  List<LogEntry> logs(int minutes) {
    List<LogEntry> entries = new ArrayList<>();
    for (int minutesAgo = 0; minutesAgo < minutes; minutesAgo++) {
      entries.addAll(logsOfMinute(minutesAgo));
    }
    entries.sort(Comparator.comparing(LogEntry::timestamp).reversed());
    return entries;
  }

  /** Deployments, newest first; the first one is running. */
  List<Deployment> deployments() {
    List<Release> releases = profile.releases();
    List<Deployment> deployments = new ArrayList<>(releases.size());
    for (int i = releases.size() - 1; i >= 0; i--) {
      Release release = releases.get(i);
      Duration age =
          i == releases.size() - 1 && scenario == Scenario.BAD_DEPLOYMENT
              ? Duration.ofMinutes(BAD_DEPLOYMENT_MINUTES_AGO)
              : release.age();
      deployments.add(
          new Deployment(
              "deploy-%s-%s".formatted(profile.name(), release.version()),
              release.version(),
              anchor.minus(age),
              release.deployedBy(),
              release.change()));
    }
    return deployments;
  }

  ServiceHealth health() {
    String timingOut = profile.dependencies().getFirst();
    List<DependencyHealth> dependencies =
        profile.dependencies().stream()
            .map(
                name ->
                    scenario == Scenario.DOWNSTREAM_TIMEOUT && name.equals(timingOut)
                        ? new DependencyHealth(name, HealthStatus.DOWN, CLIENT_TIMEOUT_MS)
                        : new DependencyHealth(
                            name, HealthStatus.UP, 30 + Math.floorMod(name.hashCode(), 40)))
            .toList();
    int replicas = profile.replicas();
    return switch (scenario) {
      case HEALTHY -> health(HealthStatus.UP, replicas, 0, dependencies);
      case BAD_DEPLOYMENT, DOWNSTREAM_TIMEOUT ->
          health(HealthStatus.DEGRADED, replicas, 0, dependencies);
      case MEMORY_LEAK ->
          health(HealthStatus.DEGRADED, replicas - 1, OOM_KILLS_MINUTES_AGO.size(), dependencies);
    };
  }

  private ServiceHealth health(
      HealthStatus status, int ready, int restarts, List<DependencyHealth> dependencies) {
    return new ServiceHealth(
        profile.name(),
        status,
        profile.runningRelease().version(),
        profile.replicas(),
        ready,
        restarts,
        dependencies,
        anchor);
  }

  private List<LogEntry> logsOfMinute(int minutesAgo) {
    List<LogEntry> entries = new ArrayList<>();
    String pod = pod(Math.floorMod(minutesAgo, profile.replicas()));
    switch (scenario) {
      case HEALTHY -> {}
      case BAD_DEPLOYMENT -> {
        String version = profile.runningRelease().version();
        if (minutesAgo < BAD_DEPLOYMENT_MINUTES_AGO) {
          entries.add(
              entry(
                  minutesAgo,
                  10,
                  LogLevel.ERROR,
                  pod,
                  "HTTP 500 POST /api/v1/%s: unexpected error in version %s"
                      .formatted(profile.name(), version)));
          if (minutesAgo % STACK_TRACE_INTERVAL_MINUTES == 0) {
            entries.add(entry(minutesAgo, 20, LogLevel.ERROR, pod, nullPointerStackTrace()));
          }
        } else if (minutesAgo == BAD_DEPLOYMENT_MINUTES_AGO) {
          entries.add(
              entry(
                  minutesAgo,
                  5,
                  LogLevel.INFO,
                  pod,
                  "Started %s version %s".formatted(profile.name(), version)));
        }
      }
      case MEMORY_LEAK -> {
        double memory = leakingMemory(minutesAgo);
        if (memory > GC_PRESSURE_MEMORY_PERCENT && minutesAgo % GC_WARNING_INTERVAL_MINUTES == 0) {
          entries.add(
              entry(
                  minutesAgo,
                  15,
                  LogLevel.WARN,
                  pod(LEAKING_POD),
                  "GC pause of %d ms (G1 Old Generation); heap %.0f%% used"
                      .formatted(Math.round((memory - GC_PRESSURE_MEMORY_PERCENT) * 90), memory)));
        }
        if (OOM_KILLS_MINUTES_AGO.contains(minutesAgo)) {
          entries.add(
              entry(
                  minutesAgo,
                  40,
                  LogLevel.ERROR,
                  pod(LEAKING_POD),
                  "java.lang.OutOfMemoryError: Java heap space"));
          entries.add(
              entry(
                  minutesAgo,
                  45,
                  LogLevel.WARN,
                  pod(LEAKING_POD),
                  "Pod %s restarted: container killed (OOMKilled)".formatted(pod(LEAKING_POD))));
        }
      }
      case DOWNSTREAM_TIMEOUT -> {
        String dependency = profile.dependencies().getFirst();
        if (minutesAgo < TIMEOUT_ONSET_MINUTES_AGO) {
          entries.add(
              entry(
                  minutesAgo,
                  10,
                  LogLevel.ERROR,
                  pod,
                  "Call to %s timed out after %d ms".formatted(dependency, CLIENT_TIMEOUT_MS)));
          if (minutesAgo % RETRY_WARNING_INTERVAL_MINUTES == 0) {
            entries.add(
                entry(
                    minutesAgo,
                    25,
                    LogLevel.WARN,
                    pod,
                    "Retries exhausted for %s (2 of 2)".formatted(dependency)));
          }
        }
      }
    }
    if (minutesAgo % SUMMARY_INTERVAL_MINUTES == 0) {
      entries.add(
          entry(
              minutesAgo,
              30,
              LogLevel.INFO,
              pod,
              "Handled %d requests in the last %d minutes"
                  .formatted(
                      Math.round(metricsAt(minutesAgo).requestsPerSecond() * 60)
                          * SUMMARY_INTERVAL_MINUTES,
                      SUMMARY_INTERVAL_MINUTES)));
    }
    return entries;
  }

  private LogEntry entry(
      int minutesAgo, int secondsEarlier, LogLevel level, String pod, String message) {
    return new LogEntry(at(minutesAgo).minusSeconds(secondsEarlier), level, pod, message, false);
  }

  private String nullPointerStackTrace() {
    String packageName = "com.example." + profile.name();
    StringBuilder trace =
        new StringBuilder(
            "java.lang.NullPointerException: Cannot invoke \"java.math.BigDecimal.multiply"
                + "(java.math.BigDecimal)\" because \"adjustment.rate\" is null");
    for (String frame :
        List.of(
            "pricing.AdjustmentCalculator.apply(AdjustmentCalculator.java:87)",
            "pricing.AdjustmentCalculator.applyAll(AdjustmentCalculator.java:52)",
            "api.RequestHandler.price(RequestHandler.java:141)",
            "api.RequestHandler.handle(RequestHandler.java:96)",
            "api.RequestResource.post(RequestResource.java:44)",
            "api.RequestResource$$Subclass.post(Unknown Source)",
            "api.RequestResource_ClientProxy.post(Unknown Source)")) {
      trace.append("\n\tat ").append(packageName).append('.').append(frame);
    }
    return trace.toString();
  }

  private double leakingMemory(int minutesAgo) {
    if (minutesAgo < OOM_PHASE_MINUTES_AGO) {
      // Restarted pods start low and fill up again before the next kill.
      return 70 + Math.floorMod(minutesAgo * 13, 27);
    }
    if (minutesAgo >= LEAK_START_MINUTES_AGO) {
      return BASE_MEMORY_PERCENT;
    }
    return Math.min(
        MAX_MEMORY_PERCENT,
        BASE_MEMORY_PERCENT + (LEAK_START_MINUTES_AGO - minutesAgo) * LEAK_PERCENT_PER_MINUTE);
  }

  private String pod(int index) {
    return profile.name() + "-" + index;
  }

  /** A deterministic value in [-1, 1] that differs per minute and service. */
  private double jitter(int minutesAgo) {
    return (Math.floorMod(minutesAgo * 7919 + profile.name().hashCode(), 11) - 5) / 5.0;
  }

  private static double round(double value) {
    return Math.round(value * 10) / 10.0;
  }
}
