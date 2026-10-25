package dev.mfa.incident.service;

import dev.mfa.incident.config.KnowledgeConfig;
import dev.mfa.incident.dto.KnowledgeSectionDto;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Splits a Markdown body into sections at headings {@code #} to {@code ###} (A-0013). A section
 * longer than {@code incident.knowledge.max-segment-length} is split at blank lines, and a single
 * longer paragraph at the last whitespace before the limit. Headings inside fenced code blocks do
 * not split; sections without text are dropped.
 */
@ApplicationScoped
public class MarkdownSplitter {

  private static final Pattern HEADING = Pattern.compile("^#{1,3}\\s+(.+?)\\s*#*\\s*$");
  private static final String FENCE = "```";

  private final int maxLength;

  @Inject
  public MarkdownSplitter(KnowledgeConfig config) {
    this(config.maxSegmentLength());
  }

  MarkdownSplitter(int maxLength) {
    this.maxLength = maxLength;
  }

  public List<KnowledgeSectionDto> split(String body) {
    List<KnowledgeSectionDto> sections = new ArrayList<>();
    String heading = null;
    StringBuilder text = new StringBuilder();
    boolean inFence = false;
    for (String line : body.split("\n", -1)) {
      if (line.strip().startsWith(FENCE)) {
        inFence = !inFence;
      }
      Matcher match = inFence ? null : HEADING.matcher(line);
      if (match != null && match.matches()) {
        addSection(sections, heading, text.toString());
        heading = match.group(1);
        text.setLength(0);
      } else {
        text.append(line).append('\n');
      }
    }
    addSection(sections, heading, text.toString());
    return sections;
  }

  private void addSection(List<KnowledgeSectionDto> sections, String heading, String text) {
    for (String part : limit(text.strip())) {
      sections.add(new KnowledgeSectionDto(heading, part));
    }
  }

  private List<String> limit(String text) {
    List<String> parts = new ArrayList<>();
    StringBuilder current = new StringBuilder();
    for (String paragraph : text.split("\n\\s*\n")) {
      String trimmed = paragraph.strip();
      if (trimmed.isEmpty()) {
        continue;
      }
      if (!current.isEmpty() && current.length() + 2 + trimmed.length() > maxLength) {
        parts.add(current.toString());
        current.setLength(0);
      }
      while (trimmed.length() > maxLength) {
        int cut = trimmed.lastIndexOf(' ', maxLength);
        int end = cut > 0 ? cut : maxLength;
        parts.add(trimmed.substring(0, end).strip());
        trimmed = trimmed.substring(end).strip();
      }
      if (!current.isEmpty()) {
        current.append("\n\n");
      }
      current.append(trimmed);
    }
    if (!current.isEmpty()) {
      parts.add(current.toString());
    }
    return parts;
  }
}
