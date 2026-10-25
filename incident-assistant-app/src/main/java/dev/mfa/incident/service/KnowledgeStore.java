package dev.mfa.incident.service;

import dev.mfa.incident.dto.KnowledgeFileDto;
import dev.mfa.incident.dto.KnowledgeSectionDto;
import dev.mfa.incident.dto.RetrievedSegmentDto;
import dev.mfa.incident.dto.StoredKnowledgeDocumentDto;
import dev.mfa.incident.mapper.KnowledgeMapper;
import dev.mfa.incident.model.KnowledgeDocument;
import dev.mfa.incident.repository.KnowledgeDocumentRepository;
import dev.mfa.incident.repository.KnowledgeSegmentRepository;
import dev.mfa.incident.repository.KnowledgeSegmentRepository.ScoredSegment;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Writes of the knowledge base, each document in a short transaction of its own (P-0021); the
 * embedding calls happen before, outside any transaction.
 */
@ApplicationScoped
public class KnowledgeStore {

  private final KnowledgeDocumentRepository documents;
  private final KnowledgeSegmentRepository segments;
  private final KnowledgeMapper mapper;

  public KnowledgeStore(
      KnowledgeDocumentRepository documents,
      KnowledgeSegmentRepository segments,
      KnowledgeMapper mapper) {
    this.documents = documents;
    this.segments = segments;
    this.mapper = mapper;
  }

  /** Stored documents by path. */
  @Transactional
  public Map<String, StoredKnowledgeDocumentDto> storedDocuments() {
    return documents.listAll().stream()
        .collect(
            Collectors.toMap(
                KnowledgeDocument::getPath,
                d -> new StoredKnowledgeDocumentDto(d.getContentHash(), d.getEmbeddingModel())));
  }

  @Transactional
  public List<String> embeddingModels() {
    return documents.listEmbeddingModels();
  }

  /**
   * Stores the document with these segments, replacing what was stored for its path.
   *
   * @param embeddings one per section, in the same order
   */
  @Transactional
  public void replace(
      KnowledgeFileDto file,
      String embeddingModel,
      List<KnowledgeSectionDto> sections,
      List<float[]> embeddings) {
    KnowledgeDocument document =
        documents.findByPath(file.path()).orElseGet(KnowledgeDocument::new);
    mapper.copy(file, embeddingModel, document);
    if (document.getId() == null) {
      documents.persist(document);
    } else {
      segments.deleteByDocument(document.getId());
    }
    for (int position = 0; position < sections.size(); position++) {
      segments.persist(
          mapper.toSegment(
              document.getId(), position, sections.get(position), embeddings.get(position)));
    }
  }

  /**
   * The stored segments most similar to {@code embedding}, best first, with their document.
   *
   * @param service only that service's documents and general ones; all when {@code null}
   */
  @Transactional
  public List<RetrievedSegmentDto> findSimilar(
      float[] embedding, String embeddingModel, String service, int limit, double minScore) {
    List<ScoredSegment> found =
        segments.searchSimilar(embedding, embeddingModel, service, limit, minScore);
    Map<UUID, KnowledgeDocument> byId =
        documents
            .list("id in ?1", found.stream().map(s -> s.segment().getDocumentId()).toList())
            .stream()
            .collect(Collectors.toMap(KnowledgeDocument::getId, Function.identity()));
    return found.stream()
        .map(s -> mapper.toRetrieved(s.segment(), byId.get(s.segment().getDocumentId()), s.score()))
        .toList();
  }

  /** Removes the document and, by cascade, its segments. */
  @Transactional
  public void remove(String path) {
    documents.deleteByPath(path);
  }
}
