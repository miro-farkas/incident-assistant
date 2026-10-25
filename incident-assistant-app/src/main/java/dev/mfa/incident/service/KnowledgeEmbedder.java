package dev.mfa.incident.service;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.List;
import org.eclipse.microprofile.faulttolerance.Retry;

/**
 * Embeds texts with the configured embedding model (A-0013). Each call has the model client's
 * timeout ({@code quarkus.langchain4j.ollama.timeout}) and is retried on failure (P-0008); limits
 * in {@code application.properties}.
 */
@ApplicationScoped
public class KnowledgeEmbedder {

  private final EmbeddingModel model;

  public KnowledgeEmbedder(EmbeddingModel model) {
    this.model = model;
  }

  /**
   * @return one vector per text, in the same order
   */
  @Retry
  public List<float[]> embed(List<String> texts) {
    List<Embedding> embeddings =
        model.embedAll(texts.stream().map(TextSegment::from).toList()).content();
    if (embeddings.size() != texts.size()) {
      throw new IllegalStateException(
          "Embedding model returned " + embeddings.size() + " vectors for " + texts.size());
    }
    return embeddings.stream().map(Embedding::vector).toList();
  }

  @Retry
  public float[] embed(String text) {
    return embed(List.of(text)).getFirst();
  }
}
