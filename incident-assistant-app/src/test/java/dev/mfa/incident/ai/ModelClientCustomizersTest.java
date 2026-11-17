package dev.mfa.incident.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.RETURNS_SELF;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import dev.langchain4j.model.anthropic.AnthropicChatModel;
import dev.langchain4j.model.ollama.OllamaChatModel;
import io.quarkiverse.langchain4j.ModelBuilderCustomizer;
import io.quarkiverse.langchain4j.ModelName;
import java.util.List;
import org.junit.jupiter.api.Test;

/** The model clients of the AI steps do not retry on their own (P-0008). */
class ModelClientCustomizersTest {

  @Test
  void ollamaClientsMakeOneAttempt() {
    for (ModelBuilderCustomizer<OllamaChatModel.OllamaChatModelBuilder> customizer :
        List.of(
            new ModelClientCustomizers.TriageOllama(),
            new ModelClientCustomizers.DiagnosisOllama())) {
      OllamaChatModel.OllamaChatModelBuilder builder =
          mock(OllamaChatModel.OllamaChatModelBuilder.class, RETURNS_SELF);

      customizer.customize(builder);

      verify(builder).maxRetries(0);
    }
  }

  @Test
  void anthropicClientsMakeOneAttempt() {
    for (ModelBuilderCustomizer<AnthropicChatModel.AnthropicChatModelBuilder> customizer :
        List.of(
            new ModelClientCustomizers.TriageAnthropic(),
            new ModelClientCustomizers.DiagnosisAnthropic())) {
      AnthropicChatModel.AnthropicChatModelBuilder builder =
          mock(AnthropicChatModel.AnthropicChatModelBuilder.class, RETURNS_SELF);

      customizer.customize(builder);

      verify(builder).maxRetries(0);
    }
  }

  @Test
  void customizersApplyToTheirModelOnly() {
    assertThat(modelName(ModelClientCustomizers.TriageOllama.class)).isEqualTo("triage");
    assertThat(modelName(ModelClientCustomizers.TriageAnthropic.class)).isEqualTo("triage");
    assertThat(modelName(ModelClientCustomizers.DiagnosisOllama.class)).isEqualTo("diagnosis");
    assertThat(modelName(ModelClientCustomizers.DiagnosisAnthropic.class)).isEqualTo("diagnosis");
  }

  private static String modelName(Class<?> customizer) {
    return customizer.getAnnotation(ModelName.class).value();
  }
}
