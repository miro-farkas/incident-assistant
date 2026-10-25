package dev.mfa.incident.repository;

import dev.mfa.incident.model.KnowledgeSegment;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@ApplicationScoped
public class KnowledgeSegmentRepository implements PanacheRepositoryBase<KnowledgeSegment, UUID> {

  /** A segment and its cosine similarity to the query (1 = same direction). */
  public record ScoredSegment(KnowledgeSegment segment, double score) {}

  public long deleteByDocument(UUID documentId) {
    return delete("documentId", documentId);
  }

  /**
   * The segments most similar to {@code embedding} by cosine similarity, best first, embedded by
   * {@code embeddingModel} and with at least {@code minScore}. With a {@code service}, only
   * segments of that service's documents and of general documents (no service) are searched. Exact
   * search over all candidates (P-0021).
   */
  @SuppressWarnings("unchecked")
  public List<ScoredSegment> searchSimilar(
      float[] embedding, String embeddingModel, String service, int limit, double minScore) {
    List<Object[]> rows =
        getEntityManager()
            .createNativeQuery(
                "select s.id, 1 - (s.embedding <=> cast(:query as vector)) as score"
                    + " from knowledge_segment s"
                    + " join knowledge_document d on d.id = s.document_id"
                    + " where d.embedding_model = :model"
                    + " and (cast(:service as varchar) is null or d.service = :service"
                    + " or d.service is null)"
                    + " and 1 - (s.embedding <=> cast(:query as vector)) >= :minScore"
                    + " order by s.embedding <=> cast(:query as vector)"
                    + " limit :limit")
            .setParameter("query", vectorLiteral(embedding))
            .setParameter("model", embeddingModel)
            .setParameter("service", service)
            .setParameter("minScore", minScore)
            .setParameter("limit", limit)
            .getResultList();
    if (rows.isEmpty()) {
      return List.of();
    }
    Map<UUID, KnowledgeSegment> segments =
        list("id in ?1", rows.stream().map(row -> (UUID) row[0]).toList()).stream()
            .collect(Collectors.toMap(KnowledgeSegment::getId, Function.identity()));
    return rows.stream()
        .map(row -> new ScoredSegment(segments.get((UUID) row[0]), ((Number) row[1]).doubleValue()))
        .toList();
  }

  /** pgvector's text form, {@code [0.1,0.2]}; built from floats only, so nothing is injected. */
  private static String vectorLiteral(float[] embedding) {
    StringBuilder text = new StringBuilder("[");
    for (int i = 0; i < embedding.length; i++) {
      if (i > 0) {
        text.append(',');
      }
      text.append(embedding[i]);
    }
    return text.append(']').toString();
  }
}
