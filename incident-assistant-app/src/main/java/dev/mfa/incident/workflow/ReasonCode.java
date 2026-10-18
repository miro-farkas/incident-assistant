package dev.mfa.incident.workflow;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Machine-readable reason for entering a state such as {@code ESCALATED}, for example {@code
 * BUDGET_EXCEEDED}. Part of the public event contract (ADR-0012), so free text is not allowed.
 */
public record ReasonCode(String value) {

  public static final int MAX_LENGTH = 64;
  private static final Pattern FORMAT =
      Pattern.compile("[A-Z][A-Z0-9_]{0," + (MAX_LENGTH - 1) + "}");

  public ReasonCode {
    Objects.requireNonNull(value, "value");
    if (!FORMAT.matcher(value).matches()) {
      throw new IllegalArgumentException(
          "Reason code must be UPPER_SNAKE_CASE, at most " + MAX_LENGTH + " characters");
    }
  }

  @Override
  public String toString() {
    return value;
  }
}
