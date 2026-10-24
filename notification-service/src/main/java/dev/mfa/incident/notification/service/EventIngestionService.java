package dev.mfa.incident.notification.service;

import dev.mfa.incident.notification.dto.EventRecordingResult;
import dev.mfa.incident.notification.dto.IncidentStateEventDto;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.faulttolerance.Retry;

/**
 * Entry point for incident events. A redelivered or older event changes nothing, so the consumer
 * can process every record at least once.
 */
@ApplicationScoped
public class EventIngestionService {

  private final EventRecorder recorder;

  public EventIngestionService(EventRecorder recorder) {
    this.recorder = recorder;
  }

  /**
   * Failures such as a lost database connection or a concurrent recording of the same event are
   * retried, each attempt in a new transaction (limits in {@code application.properties}); when the
   * retries are used up the exception reaches the caller.
   */
  @Retry
  public EventRecordingResult ingest(IncidentStateEventDto event) {
    return recorder.record(event);
  }
}
