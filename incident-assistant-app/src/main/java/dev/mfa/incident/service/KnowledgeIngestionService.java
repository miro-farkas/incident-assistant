package dev.mfa.incident.service;

import dev.mfa.incident.config.KnowledgeConfig;
import dev.mfa.incident.dto.IngestionReportDto;
import dev.mfa.incident.dto.KnowledgeDirectoryDto;
import dev.mfa.incident.dto.KnowledgeFileDto;
import dev.mfa.incident.dto.KnowledgeSectionDto;
import dev.mfa.incident.dto.StoredKnowledgeDocumentDto;
import dev.mfa.incident.exception.IngestionInProgressException;
import dev.mfa.incident.repository.KnowledgeDocumentRepository;
import dev.mfa.incident.repository.KnowledgeDocumentRepository.IngestionLock;
import jakarta.enterprise.context.ApplicationScoped;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jboss.logging.Logger;

/**
 * Brings the knowledge base in line with the knowledge directory (P-0005, P-0021, A-0013): a new
 * file is added, a file whose content or embedding model changed is re-embedded and replaces its
 * segments, an unchanged one is left alone, and a document whose file is gone is removed. One run
 * at a time across all instances; embedding happens outside any transaction, each document is
 * written in its own.
 */
@ApplicationScoped
public class KnowledgeIngestionService {

  private static final Logger LOG = Logger.getLogger(KnowledgeIngestionService.class);

  private final KnowledgeFileReader reader;
  private final MarkdownSplitter splitter;
  private final KnowledgeEmbedder embedder;
  private final KnowledgeStore store;
  private final KnowledgeDocumentRepository locks;
  private final KnowledgeConfig config;

  public KnowledgeIngestionService(
      KnowledgeFileReader reader,
      MarkdownSplitter splitter,
      KnowledgeEmbedder embedder,
      KnowledgeStore store,
      KnowledgeDocumentRepository locks,
      KnowledgeConfig config) {
    this.reader = reader;
    this.splitter = splitter;
    this.embedder = embedder;
    this.store = store;
    this.locks = locks;
    this.config = config;
  }

  /** Ingests the configured knowledge directory. */
  public IngestionReportDto ingest() {
    return ingest(config.directory());
  }

  /**
   * @throws IngestionInProgressException if another run holds the lock
   * @throws IllegalStateException if the directory is missing; nothing is changed then
   */
  public IngestionReportDto ingest(Path directory) {
    try (IngestionLock lock =
        locks.tryLockIngestion().orElseThrow(IngestionInProgressException::new)) {
      return run(directory);
    }
  }

  private IngestionReportDto run(Path directory) {
    KnowledgeDirectoryDto found = reader.read(directory);
    Map<String, StoredKnowledgeDocumentDto> stored = store.storedDocuments();
    String model = config.embeddingModel();
    int added = 0;
    int updated = 0;
    int unchanged = 0;
    Set<String> present = new HashSet<>();
    for (KnowledgeFileDto file : found.files()) {
      present.add(file.path());
      StoredKnowledgeDocumentDto previous = stored.get(file.path());
      if (previous != null
          && previous.contentHash().equals(file.contentHash())
          && previous.embeddingModel().equals(model)) {
        unchanged++;
        continue;
      }
      List<KnowledgeSectionDto> sections = splitter.split(file.body());
      List<float[]> embeddings =
          sections.isEmpty()
              ? List.of()
              : embedder.embed(sections.stream().map(s -> embeddingText(file, s)).toList());
      store.replace(file, model, sections, embeddings);
      if (previous == null) {
        added++;
      } else {
        updated++;
      }
    }
    // A file that is present but unreadable keeps its last good version.
    found.skipped().forEach(skipped -> present.add(skipped.path()));
    int removed = 0;
    for (String path : stored.keySet()) {
      if (!present.contains(path)) {
        store.remove(path);
        removed++;
      }
    }
    found.skipped().forEach(s -> LOG.warnf("Skipped knowledge file %s: %s", s.path(), s.reason()));
    LOG.infof(
        "Knowledge ingested: %d added, %d updated, %d unchanged, %d removed, %d skipped",
        added, updated, unchanged, removed, found.skipped().size());
    return new IngestionReportDto(added, updated, unchanged, removed, found.skipped());
  }

  /** The title and heading give a section the context its text alone lacks. */
  private static String embeddingText(KnowledgeFileDto file, KnowledgeSectionDto section) {
    return section.heading() == null
        ? file.title() + " — " + section.text()
        : file.title() + " — " + section.heading() + " — " + section.text();
  }
}
