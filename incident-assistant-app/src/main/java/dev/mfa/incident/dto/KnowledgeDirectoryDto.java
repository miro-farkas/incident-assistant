package dev.mfa.incident.dto;

import java.util.List;

/**
 * The readable documents of the knowledge directory and the files that are not.
 *
 * @param files in path order
 */
public record KnowledgeDirectoryDto(
    List<KnowledgeFileDto> files, List<IngestionReportDto.SkippedFile> skipped) {}
