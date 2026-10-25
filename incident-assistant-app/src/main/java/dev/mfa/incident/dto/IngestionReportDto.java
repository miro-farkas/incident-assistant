package dev.mfa.incident.dto;

import java.util.List;

/**
 * Outcome of one knowledge ingestion run (A-0013).
 *
 * @param added new documents
 * @param updated documents whose file or embedding model changed, re-embedded
 * @param unchanged documents left as they were
 * @param removed documents whose file is gone
 * @param skipped files that could not be read as knowledge documents
 */
public record IngestionReportDto(
    int added, int updated, int unchanged, int removed, List<SkippedFile> skipped) {

  /**
   * @param reason what is wrong with the file, without its content
   */
  public record SkippedFile(String path, String reason) {}
}
