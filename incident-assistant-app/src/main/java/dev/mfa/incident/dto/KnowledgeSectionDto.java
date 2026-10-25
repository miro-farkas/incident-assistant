package dev.mfa.incident.dto;

/**
 * A part of a document to embed.
 *
 * @param heading of the section; {@code null} for text before the first heading
 */
public record KnowledgeSectionDto(String heading, String text) {}
