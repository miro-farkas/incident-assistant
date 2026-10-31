package dev.mfa.incident.ai;

import dev.mfa.incident.dto.RetrievedSegmentDto;
import java.util.List;

/**
 * Search of the knowledge base as the diagnosis agent's tool needs it (A-0015). Implemented in the
 * service layer, so this package keeps depending on {@code dto} and {@code config} only (P-0013).
 */
public interface KnowledgeSearch {

  /**
   * The segments most similar to {@code query}, best first, with the configured limit and minimum
   * score.
   *
   * @param service only that service's documents and general ones; all documents when {@code null}
   */
  List<RetrievedSegmentDto> search(String query, String service);
}
