package dev.mfa.incident.exception;

/** Another knowledge ingestion holds the lock (A-0013); the caller may try again later. */
public class IngestionInProgressException extends RuntimeException {

  public IngestionInProgressException() {
    super("Another knowledge ingestion is running; try again when it has finished");
  }
}
