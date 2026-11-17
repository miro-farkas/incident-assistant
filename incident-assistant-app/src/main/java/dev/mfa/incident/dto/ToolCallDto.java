package dev.mfa.incident.dto;

import java.util.List;
import java.util.Objects;

/**
 * One tool call an AI step requested, as stored in the step's tool call trace (A-0015). The
 * arguments are the model's text and cut at {@link #MAX_ARGUMENTS_LENGTH}; tool results are not
 * stored.
 *
 * @param durationMs 0 for a call that was not executed
 * @param resultChars length of the result text the model received; 0 when not executed
 * @param titles document titles a knowledge search returned; empty for other tools
 */
public record ToolCallDto(
    String tool,
    String arguments,
    Outcome outcome,
    long durationMs,
    int resultChars,
    List<String> titles) {

  public static final int MAX_ARGUMENTS_LENGTH = 1000;

  public enum Outcome {
    /** Executed, result returned to the model. */
    OK,
    /** Executed, the tool reported an error to the model. */
    ERROR,
    /** Not executed, because the tool call budget was used up. */
    NOT_EXECUTED
  }

  public ToolCallDto {
    Objects.requireNonNull(tool, "tool");
    Objects.requireNonNull(outcome, "outcome");
    arguments = arguments == null ? "" : arguments;
    if (arguments.length() > MAX_ARGUMENTS_LENGTH) {
      arguments = arguments.substring(0, MAX_ARGUMENTS_LENGTH);
    }
    titles = titles == null ? List.of() : List.copyOf(titles);
  }
}
