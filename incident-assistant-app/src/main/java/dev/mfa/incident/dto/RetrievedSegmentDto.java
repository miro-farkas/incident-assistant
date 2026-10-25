package dev.mfa.incident.dto;

import dev.mfa.incident.model.DocumentType;

/**
 * A knowledge segment found for a query, with what a citation needs (P-0005). The text is untrusted
 * input for an LLM (P-0007).
 *
 * @param service of the document; {@code null} for a general document
 * @param heading of the section; {@code null} before the first heading
 * @param score cosine similarity to the query, 0 to 1
 */
public record RetrievedSegmentDto(
    String title,
    String path,
    String service,
    DocumentType type,
    String heading,
    String text,
    double score) {}
