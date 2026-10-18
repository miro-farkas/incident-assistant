package dev.mfa.incident.workflow;

import java.util.Objects;

/** Argument checks shared by the workflow domain records. */
final class Validation {

  private Validation() {}

  static void requireText(String value, String name, int maxLength) {
    Objects.requireNonNull(value, name);
    if (value.isBlank()) {
      throw new IllegalArgumentException(name + " must not be blank");
    }
    if (value.length() > maxLength) {
      throw new IllegalArgumentException(name + " must be at most " + maxLength + " characters");
    }
  }

  static void requireNonNegative(long value, String name) {
    if (value < 0) {
      throw new IllegalArgumentException(name + " must not be negative");
    }
  }
}
