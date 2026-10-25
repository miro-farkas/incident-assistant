package dev.mfa.incident.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.RETURNS_SELF;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import dev.langchain4j.model.anthropic.AnthropicChatModel;
import dev.langchain4j.model.ollama.OllamaChatModel;
import io.quarkiverse.langchain4j.ModelName;
import org.junit.jupiter.api.Test;

/** The model clients of the triage model do not retry on their own (P-0008). */
class TriageModelCustomizersTest {

  @Test
  void ollamaClientMakesOneAttempt() {
    OllamaChatModel.OllamaChatModelBuilder builder =
        mock(OllamaChatModel.OllamaChatModelBuilder.class, RETURNS_SELF);

    new TriageModelCustomizers.Ollama().customize(builder);

    verify(builder).maxRetries(0);
  }

  @Test
  void anthropicClientMakesOneAttempt() {
    AnthropicChatModel.AnthropicChatModelBuilder builder =
        mock(AnthropicChatModel.AnthropicChatModelBuilder.class, RETURNS_SELF);

    new TriageModelCustomizers.Anthropic().customize(builder);

    verify(builder).maxRetries(0);
  }

  @Test
  void customizersApplyToTheTriageModelOnly() {
    assertThat(TriageModelCustomizers.Ollama.class.getAnnotation(ModelName.class).value())
        .isEqualTo("triage");
    assertThat(TriageModelCustomizers.Anthropic.class.getAnnotation(ModelName.class).value())
        .isEqualTo("triage");
  }
}
