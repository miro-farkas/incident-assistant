package dev.mfa.incident.notification.dto;

/** What recording an incident event did (P-0020). */
public sealed interface EventRecordingResult {

  /**
   * The event was new and current.
   *
   * @param notifications pending notifications written; {@code 0} when no rule matched
   */
  record Recorded(int notifications) implements EventRecordingResult {}

  /** The event was processed before; nothing changed. */
  record Duplicate() implements EventRecordingResult {}

  /** A newer version of the incident was seen before; the event is recorded, nothing sent. */
  record Outdated(long lastVersion) implements EventRecordingResult {}
}
