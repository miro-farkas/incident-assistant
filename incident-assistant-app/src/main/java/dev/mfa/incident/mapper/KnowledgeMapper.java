package dev.mfa.incident.mapper;

import dev.mfa.incident.dto.KnowledgeFileDto;
import dev.mfa.incident.dto.KnowledgeSectionDto;
import dev.mfa.incident.dto.RetrievedSegmentDto;
import dev.mfa.incident.model.KnowledgeDocument;
import dev.mfa.incident.model.KnowledgeSegment;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.UUID;

@ApplicationScoped
public class KnowledgeMapper {

  /** Copies path, hash and front matter into a new or an existing document. */
  public void copy(KnowledgeFileDto file, String embeddingModel, KnowledgeDocument document) {
    if (document.getPath() == null) {
      document.setPath(file.path());
    }
    document.setContentHash(file.contentHash());
    document.setEmbeddingModel(embeddingModel);
    document.setService(file.service());
    document.setType(file.type());
    document.setTitle(file.title());
    document.setUpdated(file.updated());
  }

  public KnowledgeSegment toSegment(
      UUID documentId, int position, KnowledgeSectionDto section, float[] embedding) {
    KnowledgeSegment segment = new KnowledgeSegment();
    segment.setDocumentId(documentId);
    segment.setPosition(position);
    segment.setHeading(section.heading());
    segment.setText(section.text());
    segment.setEmbedding(embedding);
    return segment;
  }

  public RetrievedSegmentDto toRetrieved(
      KnowledgeSegment segment, KnowledgeDocument document, double score) {
    return new RetrievedSegmentDto(
        document.getTitle(),
        document.getPath(),
        document.getService(),
        document.getType(),
        segment.getHeading(),
        segment.getText(),
        score);
  }
}
