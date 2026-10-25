package dev.mfa.incident.service;

import static dev.mfa.incident.testsupport.KnowledgeFiles.document;
import static org.assertj.core.api.Assertions.assertThat;

import dev.mfa.incident.dto.RetrievedSegmentDto;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Retrieval with service filter and minimum score (P-0005, A-0013), with the fake embeddings. */
@QuarkusTest
class KnowledgeRetrieverTest {

  private static final double ANY_SCORE = 0.05;

  @Inject KnowledgeIngestionService ingestion;
  @Inject KnowledgeRetriever retriever;

  @TempDir Path directory;

  @BeforeEach
  void ingestDocuments() {
    document(
        directory,
        "inventory.md",
        "inventory",
        "runbook",
        "Inventory runbook",
        "# Memory\nHeap memory grows until pods are killed out of memory.\n"
            + "# Stock\nStock levels come from the inventory database.\n");
    document(
        directory,
        "payments.md",
        "payments",
        "runbook",
        "Payments runbook",
        "# Timeouts\nBank gateway timeouts make payments fail.\n");
    document(
        directory,
        "general.md",
        null,
        "postmortem",
        "General postmortem",
        "# Memory\nAny service whose memory grows needs a restart.\n");
    ingestion.ingest(directory);
  }

  @Test
  void bestMatchFirstWithCitation() {
    List<RetrievedSegmentDto> found =
        retriever.search("pods killed out of memory", null, 5, ANY_SCORE);

    assertThat(found).isNotEmpty();
    RetrievedSegmentDto best = found.getFirst();
    assertThat(best.title()).isEqualTo("Inventory runbook");
    assertThat(best.path()).isEqualTo("inventory.md");
    assertThat(best.heading()).isEqualTo("Memory");
    assertThat(best.text()).contains("killed out of memory");
    assertThat(found)
        .extracting(RetrievedSegmentDto::score)
        .isSortedAccordingTo((a, b) -> Double.compare(b, a));
  }

  @Test
  void serviceFilterKeepsThatServiceAndGeneralDocuments() {
    List<RetrievedSegmentDto> found = retriever.search("memory grows", "payments", 10, ANY_SCORE);

    assertThat(found)
        .extracting(RetrievedSegmentDto::path)
        .contains("general.md")
        .doesNotContain("inventory.md");
  }

  @Test
  void minimumScoreAndLimitApply() {
    assertThat(retriever.search("memory", null, 1, ANY_SCORE)).hasSize(1);
    assertThat(retriever.search("completely unrelated words", null, 10, 0.99)).isEmpty();
  }
}
