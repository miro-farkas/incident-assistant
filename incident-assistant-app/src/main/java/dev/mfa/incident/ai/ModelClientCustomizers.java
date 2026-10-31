package dev.mfa.incident.ai;

import dev.langchain4j.model.anthropic.AnthropicChatModel;
import dev.langchain4j.model.ollama.OllamaChatModel;
import io.quarkiverse.langchain4j.ModelBuilderCustomizer;
import io.quarkiverse.langchain4j.ModelName;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * Turns off the retries langchain4j performs inside the model clients of the {@code triage} and
 * {@code diagnosis} models, for both providers. Fault tolerance of the AI services is the only
 * retry (P-0008): the client's own retry also retries a call the fault-tolerance timeout
 * interrupted, so one attempt ran past its timeout. Neither extension offers a configuration
 * property for it (Ollama always sets one retry, Anthropic requires at least one).
 */
public final class ModelClientCustomizers {

  /** No retries: every attempt is one request. */
  static final int CLIENT_RETRIES = 0;

  private ModelClientCustomizers() {}

  /** Dev mode's Ollama model of the triage step. */
  @ApplicationScoped
  @ModelName("triage")
  public static class TriageOllama
      implements ModelBuilderCustomizer<OllamaChatModel.OllamaChatModelBuilder> {

    @Override
    public void customize(OllamaChatModel.OllamaChatModelBuilder builder) {
      builder.maxRetries(CLIENT_RETRIES);
    }
  }

  /** Production's Anthropic model of the triage step. */
  @ApplicationScoped
  @ModelName("triage")
  public static class TriageAnthropic
      implements ModelBuilderCustomizer<AnthropicChatModel.AnthropicChatModelBuilder> {

    @Override
    public void customize(AnthropicChatModel.AnthropicChatModelBuilder builder) {
      builder.maxRetries(CLIENT_RETRIES);
    }
  }

  /** Dev mode's Ollama model of the diagnosis step. */
  @ApplicationScoped
  @ModelName("diagnosis")
  public static class DiagnosisOllama
      implements ModelBuilderCustomizer<OllamaChatModel.OllamaChatModelBuilder> {

    @Override
    public void customize(OllamaChatModel.OllamaChatModelBuilder builder) {
      builder.maxRetries(CLIENT_RETRIES);
    }
  }

  /** Production's Anthropic model of the diagnosis step. */
  @ApplicationScoped
  @ModelName("diagnosis")
  public static class DiagnosisAnthropic
      implements ModelBuilderCustomizer<AnthropicChatModel.AnthropicChatModelBuilder> {

    @Override
    public void customize(AnthropicChatModel.AnthropicChatModelBuilder builder) {
      builder.maxRetries(CLIENT_RETRIES);
    }
  }
}
