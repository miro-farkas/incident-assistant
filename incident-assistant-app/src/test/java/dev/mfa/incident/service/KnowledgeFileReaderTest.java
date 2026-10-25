package dev.mfa.incident.service;

import static dev.mfa.incident.testsupport.KnowledgeFiles.document;
import static dev.mfa.incident.testsupport.KnowledgeFiles.write;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import dev.mfa.incident.dto.IngestionReportDto.SkippedFile;
import dev.mfa.incident.dto.KnowledgeDirectoryDto;
import dev.mfa.incident.dto.KnowledgeFileDto;
import dev.mfa.incident.model.DocumentType;
import java.nio.file.Path;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Front matter of A-0004 and the files that do not have it. */
class KnowledgeFileReaderTest {

  private final KnowledgeFileReader reader = new KnowledgeFileReader();

  @TempDir Path directory;

  @Test
  void readsFrontMatterBodyAndHashOfNestedFiles() {
    document(directory, "runbooks/checkout.md", "checkout", "runbook", "Checkout", "# A\ntext\n");
    document(directory, "general.md", null, "Postmortem", "General", "body\n");
    write(directory, "notes.txt", "not markdown");

    KnowledgeDirectoryDto read = reader.read(directory);

    assertThat(read.skipped()).isEmpty();
    assertThat(read.files())
        .extracting(KnowledgeFileDto::path, KnowledgeFileDto::service, KnowledgeFileDto::type)
        .containsExactly(
            tuple("general.md", null, DocumentType.POSTMORTEM),
            tuple("runbooks/checkout.md", "checkout", DocumentType.RUNBOOK));
    KnowledgeFileDto checkout = read.files().get(1);
    assertThat(checkout.title()).isEqualTo("Checkout");
    assertThat(checkout.updated()).isEqualTo(LocalDate.parse("2026-10-20"));
    assertThat(checkout.body()).isEqualTo("# A\ntext\n");
    assertThat(checkout.contentHash()).hasSize(64).isNotEqualTo(read.files().get(0).contentHash());
  }

  @Test
  void sameContentHasTheSameHashAndWindowsLineEndingsAreRead() {
    write(directory, "a.md", "---\r\ntype: runbook\r\ntitle: A\r\nupdated: 2026-10-20\r\n---\r\nx");
    write(
        directory, "b/a.md", "---\r\ntype: runbook\r\ntitle: A\r\nupdated: 2026-10-20\r\n---\r\nx");

    KnowledgeDirectoryDto read = reader.read(directory);

    assertThat(read.files()).hasSize(2);
    assertThat(read.files().get(0).contentHash()).isEqualTo(read.files().get(1).contentHash());
    assertThat(read.files().get(0).body()).isEqualTo("x");
  }

  @Test
  void reportsFilesThatAreNotKnowledgeDocuments() {
    write(directory, "no-front-matter.md", "# Just text\n");
    write(directory, "unclosed.md", "---\ntype: runbook\ntitle: X\n");
    document(directory, "unknown-type.md", "checkout", "howto", "X", "x");
    document(directory, "no-title.md", "checkout", "runbook", "", "x");
    write(directory, "bad-date.md", "---\ntype: runbook\ntitle: X\nupdated: yesterday\n---\nx");
    write(directory, "not-yaml.md", "---\ntype: [runbook\n---\nx");

    KnowledgeDirectoryDto read = reader.read(directory);

    assertThat(read.files()).isEmpty();
    assertThat(read.skipped())
        .extracting(SkippedFile::path, SkippedFile::reason)
        .containsExactly(
            tuple("bad-date.md", "updated must be a date YYYY-MM-DD"),
            tuple("no-front-matter.md", "no front matter"),
            tuple("no-title.md", "title is missing"),
            tuple("not-yaml.md", "front matter is not valid YAML"),
            tuple("unclosed.md", "front matter not closed"),
            tuple("unknown-type.md", "type must be runbook or postmortem"));
  }

  @Test
  void missingDirectoryIsAnError() {
    assertThatThrownBy(() -> reader.read(directory.resolve("missing")))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("not found");
  }
}
