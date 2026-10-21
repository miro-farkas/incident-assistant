package dev.mfa.incident.exception;

import java.time.Duration;

/** The instance already serves the configured maximum number of event streams (A-0009). */
public final class StreamLimitExceededException extends RuntimeException {

  private final Duration retryAfter;

  /**
   * @param retryAfter how long the client should wait before trying again
   */
  public StreamLimitExceededException(int maxStreams, Duration retryAfter) {
    super("This instance already serves " + maxStreams + " event streams");
    this.retryAfter = retryAfter;
  }

  public Duration retryAfter() {
    return retryAfter;
  }
}
