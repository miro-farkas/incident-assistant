package dev.mfa.incident.dto;

import dev.mfa.incident.model.DocumentType;
import java.time.LocalDate;

/**
 * A Markdown file of the knowledge directory with its front matter (A-0004).
 *
 * @param path relative to the knowledge directory, with forward slashes
 * @param contentHash SHA-256 of the whole file, hex
 * @param service {@code null} for a general document
 * @param body the Markdown after the front matter
 */
public record KnowledgeFileDto(
    String path,
    String contentHash,
    String service,
    DocumentType type,
    String title,
    LocalDate updated,
    String body) {}
