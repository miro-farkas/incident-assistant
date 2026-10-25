package dev.mfa.incident.config;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.nio.file.Path;

/** The knowledge base: its files, ingestion and retrieval (A-0004, A-0013, P-0021). */
@ConfigMapping(prefix = "incident.knowledge")
public interface KnowledgeConfig {

  /** Directory with the Markdown documents; subdirectories are included. */
  Path directory();

  /** Ingest when the application starts; also re-embeds everything after a model change. */
  @WithDefault("false")
  boolean ingestOnStartup();

  /** Id stored with every document, naming the embedding model, for example ollama/model. */
  @NotBlank
  @Size(max = 200)
  String embeddingModel();

  /** A section longer than this is split at paragraph boundaries (characters). */
  @WithDefault("1500")
  @Min(200)
  @Max(8000)
  int maxSegmentLength();

  Retrieval retrieval();

  interface Retrieval {

    /** Segments returned at most. */
    @WithDefault("5")
    @Min(1)
    @Max(50)
    int limit();

    /** Lowest cosine similarity of a returned segment. */
    @WithDefault("0.6")
    @DecimalMin("0.0")
    @DecimalMax("1.0")
    double minScore();
  }
}
