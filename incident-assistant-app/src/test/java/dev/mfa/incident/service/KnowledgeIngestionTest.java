package dev.mfa.incident.service;

import static dev.mfa.incident.testsupport.KnowledgeFiles.document;
import static dev.mfa.incident.testsupport.KnowledgeFiles.write;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.mfa.incident.dto.IngestionReportDto;
import dev.mfa.incident.exception.IngestionInProgressException;
import dev.mfa.incident.repository.KnowledgeDocumentRepository;
import dev.mfa.incident.repository.KnowledgeDocumentRepository.IngestionLock;
import dev.mfa.incident.testsupport.FakeEmbeddingModel;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Idempotent ingestion (P-0005, P-0021): added, updated, unchanged, removed, skipped, and the
 * embedding model check. Each test starts with an empty knowledge base and ingests a directory of
 * its own.
 */
@QuarkusTest
class KnowledgeIngestionTest {

  @Inject KnowledgeIngestionService ingestion;
  @Inject KnowledgeStartup startup;
  @Inject KnowledgeDocumentRepository documents;
  @Inject FakeEmbeddingModel embeddings;
  @Inject EntityManager entityManager;

  @TempDir Path directory;

  /** Ingesting an empty directory removes every document, so each test starts empty. */
  @BeforeEach
  void startWithAnEmptyKnowledgeBase(@TempDir Path empty) {
    ingestion.ingest(empty);
  }

  @Test
  void unchangedFilesAreSkippedChangedReplacedAndDeletedRemoved() throws IOException {
    Path changing = document(directory, "a.md", "checkout", "runbook", "A", "# One\nfirst\n");
    Path deleted = document(directory, "b.md", "payments", "runbook", "B", "# One\nsecond\n");
    assertThat(ingestion.ingest(directory)).isEqualTo(report(2, 0, 0, 0));

    int embeddedBefore = embeddings.embeddedTexts();
    assertThat(ingestion.ingest(directory)).isEqualTo(report(0, 0, 2, 0));
    assertThat(embeddings.embeddedTexts()).as("nothing re-embedded").isEqualTo(embeddedBefore);

    document(directory, "a.md", "checkout", "runbook", "A", "# One\nchanged\n# Two\nmore\n");
    Files.delete(deleted);
    document(directory, "c.md", null, "postmortem", "C", "third\n");

    assertThat(ingestion.ingest(directory)).isEqualTo(report(1, 1, 0, 1));
    assertThat(embeddings.embeddedTexts() - embeddedBefore)
        .as("a.md (2) and c.md (1)")
        .isEqualTo(3);
    assertThat(changing).exists();
    assertThat(segmentTexts("a.md")).containsExactly("changed", "more");
    assertThat(documents.findByPath("b.md")).isEmpty();
    assertThat(segmentCount("b.md")).isZero();
  }

  @Test
  void unreadableFileIsReportedAndKeepsItsLastGoodVersion() {
    document(directory, "a.md", "checkout", "runbook", "A", "good\n");
    ingestion.ingest(directory);
    write(directory, "a.md", "no front matter any more\n");

    IngestionReportDto report = ingestion.ingest(directory);

    assertThat(report.removed()).isZero();
    assertThat(report.skipped())
        .singleElement()
        .satisfies(s -> assertThat(s.reason()).isEqualTo("no front matter"));
    assertThat(segmentTexts("a.md")).containsExactly("good");
  }

  @Test
  void missingDirectoryChangesNothing() {
    document(directory, "a.md", "checkout", "runbook", "A", "text\n");
    ingestion.ingest(directory);

    assertThatThrownBy(() -> ingestion.ingest(directory.resolve("gone")))
        .isInstanceOf(IllegalStateException.class);
    assertThat(documents.findByPath("a.md")).isPresent();
  }

  @Test
  void otherEmbeddingModelStopsStartupUntilReingested() {
    document(directory, "a.md", "checkout", "runbook", "A", "text\n");
    ingestion.ingest(directory);
    QuarkusTransaction.requiringNew()
        .run(
            () ->
                entityManager
                    .createNativeQuery(
                        "update knowledge_document set embedding_model = 'old/model'")
                    .executeUpdate());

    assertThatThrownBy(startup::checkEmbeddingModel)
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("old/model")
        .hasMessageContaining("test/fake-embedding");

    assertThat(ingestion.ingest(directory)).isEqualTo(report(0, 1, 0, 0));
    startup.checkEmbeddingModel();
  }

  @Test
  void secondRunAtTheSameTimeIsRefused() {
    try (IngestionLock held = documents.tryLockIngestion().orElseThrow()) {
      assertThatThrownBy(() -> ingestion.ingest(directory))
          .isInstanceOf(IngestionInProgressException.class);
    }
    document(directory, "a.md", "checkout", "runbook", "A", "text\n");
    assertThat(ingestion.ingest(directory).added()).isEqualTo(1);
  }

  @Test
  void sampleKnowledgeOfTheRepositoryIsValid() {
    IngestionReportDto report = ingestion.ingest(Path.of("../knowledge"));

    assertThat(report.skipped()).isEmpty();
    assertThat(report.added() + report.updated() + report.unchanged()).isEqualTo(5);
    for (String service : List.of("checkout", "payments", "inventory")) {
      assertThat(documents.findByPath("runbooks/" + service + ".md"))
          .hasValueSatisfying(d -> assertThat(d.getService()).isEqualTo(service));
    }
  }

  private static IngestionReportDto report(int added, int updated, int unchanged, int removed) {
    return new IngestionReportDto(added, updated, unchanged, removed, List.of());
  }

  @SuppressWarnings("unchecked")
  private List<String> segmentTexts(String path) {
    return QuarkusTransaction.requiringNew()
        .call(
            () ->
                entityManager
                    .createNativeQuery(
                        "select s.text from knowledge_segment s join knowledge_document d"
                            + " on d.id = s.document_id where d.path = :path order by s.position")
                    .setParameter("path", path)
                    .getResultList());
  }

  private long segmentCount(String path) {
    return segmentTexts(path).size();
  }
}
