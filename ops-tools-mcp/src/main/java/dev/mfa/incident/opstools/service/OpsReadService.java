package dev.mfa.incident.opstools.service;

import static java.time.temporal.ChronoUnit.MINUTES;

import dev.mfa.incident.opstools.config.OpsToolsConfig;
import dev.mfa.incident.opstools.dto.Deployment;
import dev.mfa.incident.opstools.dto.DeploymentHistory;
import dev.mfa.incident.opstools.dto.LogEntry;
import dev.mfa.incident.opstools.dto.LogLevel;
import dev.mfa.incident.opstools.dto.LogSearchResult;
import dev.mfa.incident.opstools.dto.MetricPoint;
import dev.mfa.incident.opstools.dto.ServiceHealth;
import dev.mfa.incident.opstools.dto.ServiceMetrics;
import jakarta.enterprise.context.ApplicationScoped;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Answers the read tools from the simulated timeline of a service (A-0005). Results are bounded:
 * callers pass limits within the ranges the tools validate, and the hard maxima here cap what one
 * call returns (P-0006).
 */
@ApplicationScoped
public class OpsReadService {

  /** Most metric points one call returns; the newest points of a longer window are kept. */
  private static final int MAX_METRIC_POINTS = 120;

  /** Longest log message returned; longer messages are cut and marked. */
  private static final int MAX_LOG_MESSAGE_LENGTH = 500;

  private final ServiceCatalog catalog;
  private final OpsToolsConfig config;
  private final Clock clock;

  public OpsReadService(ServiceCatalog catalog, OpsToolsConfig config, Clock clock) {
    this.catalog = catalog;
    this.config = config;
    this.clock = clock;
  }

  /** Metrics over the last {@code windowMinutes}, one point every {@code stepMinutes}. */
  public ServiceMetrics metrics(String service, int windowMinutes, int stepMinutes) {
    ScenarioTimeline timeline = timeline(service);
    int available = Math.max(1, windowMinutes / stepMinutes);
    int returned = Math.min(available, MAX_METRIC_POINTS);
    List<MetricPoint> points = new ArrayList<>(returned);
    for (int index = returned - 1; index >= 0; index--) {
      points.add(timeline.metricsAt(index * stepMinutes));
    }
    return new ServiceMetrics(
        service,
        points.getFirst().timestamp(),
        points.getLast().timestamp(),
        stepMinutes,
        points,
        available > returned);
  }

  /**
   * Log entries of the last {@code sinceMinutes} with at least {@code minLevel} whose message
   * contains {@code query} (ignoring case; blank matches all), newest first, at most {@code limit}.
   */
  public LogSearchResult searchLogs(
      String service, String query, LogLevel minLevel, int sinceMinutes, int limit) {
    String needle = query == null ? "" : query.strip().toLowerCase(Locale.ROOT);
    List<LogEntry> matches =
        timeline(service).logs(sinceMinutes).stream()
            .filter(entry -> entry.level().compareTo(minLevel) >= 0)
            .filter(entry -> entry.message().toLowerCase(Locale.ROOT).contains(needle))
            .toList();
    List<LogEntry> entries = matches.stream().limit(limit).map(OpsReadService::capMessage).toList();
    return new LogSearchResult(service, matches.size(), entries, matches.size() > limit);
  }

  /** The {@code limit} most recent deployments, newest first. */
  public DeploymentHistory recentDeployments(String service, int limit) {
    List<Deployment> all = timeline(service).deployments();
    return new DeploymentHistory(
        service, all.subList(0, Math.min(limit, all.size())), all.size() > limit);
  }

  public ServiceHealth health(String service) {
    return timeline(service).health();
  }

  private ScenarioTimeline timeline(String service) {
    return new ScenarioTimeline(catalog.access(service), anchor());
  }

  private Instant anchor() {
    return config.fakeData().anchor().orElseGet(() -> clock.instant().truncatedTo(MINUTES));
  }

  private static LogEntry capMessage(LogEntry entry) {
    if (entry.message().length() <= MAX_LOG_MESSAGE_LENGTH) {
      return entry;
    }
    return new LogEntry(
        entry.timestamp(),
        entry.level(),
        entry.pod(),
        entry.message().substring(0, MAX_LOG_MESSAGE_LENGTH),
        true);
  }
}
