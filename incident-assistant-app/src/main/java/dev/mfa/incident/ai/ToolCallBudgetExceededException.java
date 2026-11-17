package dev.mfa.incident.ai;

import io.quarkiverse.langchain4j.runtime.PreventsErrorHandlerExecution;

/**
 * The model asked for more tool calls than one diagnosis may make (A-0015). It ends the agent's
 * call instead of being reported to the model as a tool error.
 */
public class ToolCallBudgetExceededException extends RuntimeException
    implements PreventsErrorHandlerExecution {

  public ToolCallBudgetExceededException(int maxToolCalls) {
    super("More than " + maxToolCalls + " tool calls requested");
  }
}
