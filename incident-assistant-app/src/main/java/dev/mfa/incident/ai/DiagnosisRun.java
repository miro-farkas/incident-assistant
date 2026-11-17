package dev.mfa.incident.ai;

import dev.mfa.incident.dto.ToolCallDto;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

/**
 * State of one call of {@link DiagnosisAgent} (A-0015): the tool call budget, the trace of the
 * calls, and what they returned that evidence may cite. The diagnosis step creates one per call and
 * runs the call through {@link #run}; the tool provider and the output guardrail find it with
 * {@link #current()} on the same thread.
 */
public final class DiagnosisRun {

  private static final ScopedValue<DiagnosisRun> CURRENT = ScopedValue.newInstance();

  private final int maxToolCalls;
  private final String service;
  private final List<ToolCallDto> toolCalls = new ArrayList<>();
  private final Set<String> documentTitles = new LinkedHashSet<>();
  private final Set<String> answeredTools = new LinkedHashSet<>();
  private int executedCalls;
  private boolean budgetExceeded;

  /**
   * @param service the triaged service, which filters every knowledge search; {@code null} for none
   */
  public DiagnosisRun(int maxToolCalls, String service) {
    if (maxToolCalls < 1) {
      throw new IllegalArgumentException("maxToolCalls must be positive");
    }
    this.maxToolCalls = maxToolCalls;
    this.service = service;
  }

  /** Runs {@code call} with this run as {@link #current()}. */
  public <T> T run(Supplier<T> call) {
    return ScopedValue.where(CURRENT, this).call(call::get);
  }

  /**
   * @throws IllegalStateException outside {@link #run}
   */
  static DiagnosisRun current() {
    return CURRENT.orElseThrow(
        () -> new IllegalStateException("The diagnosis agent is called without a DiagnosisRun"));
  }

  public int maxToolCalls() {
    return maxToolCalls;
  }

  String service() {
    return service;
  }

  /**
   * Takes one call from the budget; {@code false} and marks the budget exceeded when none is left.
   */
  synchronized boolean tryStartCall() {
    if (executedCalls >= maxToolCalls) {
      budgetExceeded = true;
      return false;
    }
    executedCalls++;
    return true;
  }

  synchronized void record(ToolCallDto call) {
    toolCalls.add(call);
    if (call.outcome() == ToolCallDto.Outcome.OK) {
      answeredTools.add(call.tool());
      documentTitles.addAll(call.titles());
    }
  }

  /** The calls the model requested, in order. */
  public synchronized List<ToolCallDto> toolCalls() {
    return List.copyOf(toolCalls);
  }

  /** Whether the model asked for a call beyond the budget. */
  public synchronized boolean budgetExceeded() {
    return budgetExceeded;
  }

  /** Titles of the documents knowledge searches returned. */
  synchronized Set<String> documentTitles() {
    return Set.copyOf(documentTitles);
  }

  /** Names of the tools that returned a result without error. */
  synchronized Set<String> answeredTools() {
    return Set.copyOf(answeredTools);
  }
}
