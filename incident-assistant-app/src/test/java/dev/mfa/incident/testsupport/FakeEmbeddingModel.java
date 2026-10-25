package dev.mfa.incident.testsupport;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Alternative;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Replaces the Ollama embedding model in tests (P-0010): a deterministic bag of words, each word
 * hashed into one of {@value #DIMENSIONS} dimensions and the vector normalised, so texts sharing
 * words are similar and identical texts get identical vectors. Counts embedded texts, so tests can
 * see what was (re-)embedded.
 */
@Alternative
@Priority(1)
@ApplicationScoped
public class FakeEmbeddingModel implements EmbeddingModel {

  public static final int DIMENSIONS = 256;

  private final AtomicInteger embeddedTexts = new AtomicInteger();

  @Override
  public Response<List<Embedding>> embedAll(List<TextSegment> segments) {
    embeddedTexts.addAndGet(segments.size());
    return Response.from(segments.stream().map(s -> embedding(s.text())).toList());
  }

  @Override
  public int dimension() {
    return DIMENSIONS;
  }

  public int embeddedTexts() {
    return embeddedTexts.get();
  }

  private static Embedding embedding(String text) {
    float[] vector = new float[DIMENSIONS];
    for (String word : text.toLowerCase(Locale.ROOT).split("[^\\p{L}\\p{N}]+")) {
      if (word.length() > 2) {
        vector[Math.floorMod(word.hashCode(), DIMENSIONS)] += 1;
      }
    }
    double norm = 0;
    for (float v : vector) {
      norm += v * v;
    }
    if (norm > 0) {
      for (int i = 0; i < vector.length; i++) {
        vector[i] /= (float) Math.sqrt(norm);
      }
    }
    return Embedding.from(vector);
  }
}
