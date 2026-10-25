package dev.mfa.incident.notification.model;

/** Delivery state of a notification (P-0020). */
public enum NotificationStatus {
  /** Waiting for its next attempt. */
  PENDING,
  /** Accepted by the channel. */
  SENT,
  /** Given up after the last attempt. */
  FAILED
}
