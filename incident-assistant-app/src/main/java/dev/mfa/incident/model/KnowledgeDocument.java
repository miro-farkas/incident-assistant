package dev.mfa.incident.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.LocalDate;

/** A Markdown file of the knowledge directory as last ingested (A-0013, P-0021). */
@Entity
@Table(name = "knowledge_document")
public class KnowledgeDocument extends BaseEntity {

  /** Path relative to the knowledge directory, with forward slashes. */
  @Column(name = "path", nullable = false, updatable = false, length = 500)
  private String path;

  /** SHA-256 of the file, hex. */
  @Column(name = "content_hash", nullable = false, length = 64)
  private String contentHash;

  /** Model that embedded the segments, for example {@code ollama/nomic-embed-text}. */
  @Column(name = "embedding_model", nullable = false, length = 200)
  private String embeddingModel;

  /** Service the document is about; {@code null} for general documents. */
  @Column(name = "service", length = 100)
  private String service;

  @Enumerated(EnumType.STRING)
  @Column(name = "doc_type", nullable = false, length = 16)
  private DocumentType type;

  @Column(name = "title", nullable = false, length = 300)
  private String title;

  @Column(name = "updated", nullable = false)
  private LocalDate updated;

  public String getPath() {
    return path;
  }

  public void setPath(String path) {
    this.path = path;
  }

  public String getContentHash() {
    return contentHash;
  }

  public void setContentHash(String contentHash) {
    this.contentHash = contentHash;
  }

  public String getEmbeddingModel() {
    return embeddingModel;
  }

  public void setEmbeddingModel(String embeddingModel) {
    this.embeddingModel = embeddingModel;
  }

  public String getService() {
    return service;
  }

  public void setService(String service) {
    this.service = service;
  }

  public DocumentType getType() {
    return type;
  }

  public void setType(DocumentType type) {
    this.type = type;
  }

  public String getTitle() {
    return title;
  }

  public void setTitle(String title) {
    this.title = title;
  }

  public LocalDate getUpdated() {
    return updated;
  }

  public void setUpdated(LocalDate updated) {
    this.updated = updated;
  }
}
