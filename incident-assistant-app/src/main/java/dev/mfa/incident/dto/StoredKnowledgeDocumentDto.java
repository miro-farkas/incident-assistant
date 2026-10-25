package dev.mfa.incident.dto;

/** What is stored about a knowledge document to decide whether its file changed (P-0021). */
public record StoredKnowledgeDocumentDto(String contentHash, String embeddingModel) {}
