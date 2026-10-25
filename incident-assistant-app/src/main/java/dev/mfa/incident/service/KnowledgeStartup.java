package dev.mfa.incident.service;

import dev.mfa.incident.config.KnowledgeConfig;
import dev.mfa.incident.exception.IngestionInProgressException;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import java.util.List;
import org.jboss.logging.Logger;

/**
 * At startup (P-0021): with {@code incident.knowledge.ingest-on-startup} the knowledge directory is
 * ingested, which also re-embeds documents of another embedding model; without it, documents of
 * another model stop the application, so results of two models are never mixed.
 */
@ApplicationScoped
public class KnowledgeStartup {

  private static final Logger LOG = Logger.getLogger(KnowledgeStartup.class);

  private final KnowledgeStore store;
  private final KnowledgeIngestionService ingestion;
  private final KnowledgeConfig config;

  public KnowledgeStartup(
      KnowledgeStore store, KnowledgeIngestionService ingestion, KnowledgeConfig config) {
    this.store = store;
    this.ingestion = ingestion;
    this.config = config;
  }

  void onStart(@Observes StartupEvent event) {
    if (config.ingestOnStartup()) {
      try {
        ingestion.ingest();
      } catch (IngestionInProgressException e) {
        LOG.info("Knowledge ingestion at startup left to the instance already running one");
      }
      return;
    }
    checkEmbeddingModel();
  }

  /**
   * @throws IllegalStateException if a stored document was embedded with another model
   */
  public void checkEmbeddingModel() {
    List<String> others =
        store.embeddingModels().stream().filter(m -> !m.equals(config.embeddingModel())).toList();
    if (!others.isEmpty()) {
      throw new IllegalStateException(
          "The knowledge base was embedded with "
              + others
              + " but incident.knowledge.embedding-model is "
              + config.embeddingModel()
              + "; re-ingest it (incident.knowledge.ingest-on-startup=true or POST"
              + " /api/v1/knowledge/ingestion)");
    }
  }
}
