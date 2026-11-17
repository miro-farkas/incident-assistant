package dev.mfa.incident.service;

import dev.mfa.incident.ai.KnowledgeSearch;
import dev.mfa.incident.config.KnowledgeConfig;
import dev.mfa.incident.dto.RetrievedSegmentDto;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.List;

/**
 * Searches the knowledge base (P-0005, A-0013): the segments most similar to a query, best first,
 * with their document's title and path for citation. The diagnosis agent uses it as a tool
 * (A-0015).
 */
@ApplicationScoped
public class KnowledgeRetriever implements KnowledgeSearch {

  private final KnowledgeEmbedder embedder;
  private final KnowledgeStore store;
  private final KnowledgeConfig config;

  public KnowledgeRetriever(
      KnowledgeEmbedder embedder, KnowledgeStore store, KnowledgeConfig config) {
    this.embedder = embedder;
    this.store = store;
    this.config = config;
  }

  /** With the configured limit and minimum score. */
  @Override
  public List<RetrievedSegmentDto> search(String query, String service) {
    return search(query, service, config.retrieval().limit(), config.retrieval().minScore());
  }

  /**
   * @param service only that service's documents and general ones; all documents when {@code null}
   * @param minScore lowest cosine similarity, 0 to 1
   */
  public List<RetrievedSegmentDto> search(
      String query, String service, int limit, double minScore) {
    if (query == null || query.isBlank()) {
      throw new IllegalArgumentException("query must not be blank");
    }
    // Embedding first, so no transaction is open while the model works.
    float[] embedding = embedder.embed(query);
    return store.findSimilar(embedding, config.embeddingModel(), service, limit, minScore);
  }
}
