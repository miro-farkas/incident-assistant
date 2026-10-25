package dev.mfa.incident.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * One embedded part of a knowledge document (A-0013). Replaced with its document; never updated.
 */
@Entity
@Table(name = "knowledge_segment")
public class KnowledgeSegment extends BaseEntity {

  @Column(name = "document_id", nullable = false, updatable = false)
  private UUID documentId;

  /** Order within the document, from 0. */
  @Column(name = "position", nullable = false, updatable = false)
  private int position;

  /** Heading of the section the text belongs to; {@code null} before the first heading. */
  @Column(name = "heading", updatable = false, length = 300)
  private String heading;

  @Column(name = "text", nullable = false, updatable = false, columnDefinition = "text")
  private String text;

  /** pgvector {@code vector} without fixed dimension (P-0021). */
  @JdbcTypeCode(SqlTypes.VECTOR)
  @Column(name = "embedding", nullable = false, updatable = false, columnDefinition = "vector")
  private float[] embedding;

  public UUID getDocumentId() {
    return documentId;
  }

  public void setDocumentId(UUID documentId) {
    this.documentId = documentId;
  }

  public int getPosition() {
    return position;
  }

  public void setPosition(int position) {
    this.position = position;
  }

  public String getHeading() {
    return heading;
  }

  public void setHeading(String heading) {
    this.heading = heading;
  }

  public String getText() {
    return text;
  }

  public void setText(String text) {
    this.text = text;
  }

  public float[] getEmbedding() {
    return embedding;
  }

  public void setEmbedding(float[] embedding) {
    this.embedding = embedding;
  }
}
