package dev.mfa.incident.ai;

import dev.langchain4j.model.anthropic.AnthropicChatModel;
import dev.langchain4j.model.ollama.OllamaChatModel;
import io.quarkiverse.langchain4j.ModelBuilderCustomizer;
import io.quarkiverse.langchain4j.ModelName;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * Turns off the retries langchain4j performs inside the model client of the {@code triage} model,
 * for both providers. Fault tolerance of {@link TriageAssistant} is the only retry (P-0008): the
 * client's own retry also retries a call the fault-tolerance timeout interrupted, so one attempt
 * ran past its timeout. Neither extension offers a configuration property for it (Ollama always
 * sets one retry, Anthropic requires at least one).
 */
public final class TriageModelCustomizers {

  /** No retries: every attempt is one request. */
  static final int CLIENT_RETRIES = 0;

  private TriageModelCustomizers() {}

  /** Dev mode's Ollama model. */
  @ApplicationScoped
  @ModelName("triage")
  public static class Ollama
      implements ModelBuilderCustomizer<OllamaChatModel.OllamaChatModelBuilder> {

    @Override
    public void customize(OllamaChatModel.OllamaChatModelBuilder builder) {
      builder.maxRetries(CLIENT_RETRIES);
    }
  }

  /** Production's Anthropic model. */
  @ApplicationScoped
  @ModelName("triage")
  public static class Anthropic
      implements ModelBuilderCustomizer<AnthropicChatModel.AnthropicChatModelBuilder> {

    @Override
    public void customize(AnthropicChatModel.AnthropicChatModelBuilder builder) {
      builder.maxRetries(CLIENT_RETRIES);
    }
  }
}
