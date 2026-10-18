package dev.mfa.incident.dto;

import java.util.Objects;

/** Argument checks shared by the DTO records. */
final class Validation {

  private Validation() {}

  static void requireText(String value, String name) {
    Objects.requireNonNull(value, name);
    if (value.isBlank()) {
      throw new IllegalArgumentException(name + " must not be blank");
    }
  }

  static void requireText(String value, String name, int maxLength) {
    requireText(value, name);
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
