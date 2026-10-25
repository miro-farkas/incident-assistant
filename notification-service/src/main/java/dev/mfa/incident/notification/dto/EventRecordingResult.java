package dev.mfa.incident.notification.dto;

/** What recording an incident event did (P-0020). */
public sealed interface EventRecordingResult {

  /**
   * The event was new and current.
   *
   * @param notifications pending notifications written; {@code 0} when no rule matched
   */
  record Recorded(int notifications) implements EventRecordingResult {}

  /**
   * The incident's version was already seen, by this event before (a redelivery) or by a newer one;
   * nothing changed.
   */
  record Outdated(long lastVersion) implements EventRecordingResult {}
}
