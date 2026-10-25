package dev.mfa.incident.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import dev.mfa.incident.dto.KnowledgeSectionDto;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Splitting by headings with a size limit (A-0013). */
class MarkdownSplitterTest {

  private static final int MAX_LENGTH = 200;

  private final MarkdownSplitter splitter = new MarkdownSplitter(MAX_LENGTH);

  @Test
  void splitsAtHeadingsUpToLevelThree() {
    List<KnowledgeSectionDto> sections =
        splitter.split(
            "Intro text.\n\n# Service\nAbout it.\n## Symptoms ##\nErrors.\n#### Detail\nMore.\n"
                + "### Empty\n\n### Last\nEnd.\n");

    assertThat(sections)
        .extracting(KnowledgeSectionDto::heading, KnowledgeSectionDto::text)
        .containsExactly(
            tuple(null, "Intro text."),
            tuple("Service", "About it."),
            tuple("Symptoms", "Errors.\n#### Detail\nMore."),
            tuple("Last", "End."));
  }

  @Test
  void headingsInCodeBlocksDoNotSplit() {
    List<KnowledgeSectionDto> sections =
        splitter.split("# Run\n```shell\n# not a heading\necho hi\n```\nDone.\n");

    assertThat(sections)
        .singleElement()
        .satisfies(s -> assertThat(s.text()).contains("# not a heading"));
  }

  @Test
  void longSectionsAreSplitAtParagraphsThenAtWords() {
    String paragraph = "word ".repeat(30).strip();
    String longParagraph = "long ".repeat(100).strip();

    List<KnowledgeSectionDto> sections =
        splitter.split("# Big\n" + paragraph + "\n\n" + paragraph + "\n\n" + longParagraph + "\n");

    assertThat(sections)
        .allSatisfy(
            s -> {
              assertThat(s.heading()).isEqualTo("Big");
              assertThat(s.text().length()).isLessThanOrEqualTo(MAX_LENGTH);
            });
    assertThat(sections.getFirst().text()).isEqualTo(paragraph);
    assertThat(String.join(" ", sections.stream().map(KnowledgeSectionDto::text).toList()))
        .contains(longParagraph.substring(0, 100));
  }
}
