# P-0005: Retrieval-augmented generation with pgvector

- Status: Accepted
- Date: 2026-10-18

## Context
LLM answers depend on organisational knowledge that is not in the model. The knowledge is
mostly documents that change rarely and are versioned in Git.

## Decision
- Knowledge source: Markdown files in the repository with YAML front matter carrying the
  metadata used for filtering and citation (at least `type`, `title`, `updated`).
- Explicit ingestion pipeline (not "easy RAG"): parse front matter, split by Markdown
  headings with a size limit, embed, store in pgvector with metadata. Ingestion is
  idempotent by content hash: unchanged files are skipped, changed files replace their old
  segments, deleted files are removed.
- Ingestion runs on startup when enabled by config and through an admin-only REST endpoint.
- Retrieval uses metadata filters where the caller knows them, top-k with a minimum score,
  and returns source references; answers must cite document titles.
- Retrieval is exposed to an autonomous step as a tool, so it can search iteratively within
  its budget (P-0001).
- Retrieved text is untrusted input to the LLM (P-0007).
- The vector table lives in the application's PostgreSQL database.

## Alternatives considered
- Dedicated vector database (Qdrant, OpenSearch): unnecessary at this volume; one less
  component to run.
- Retrieved context injected into every prompt: wastes tokens when the step does not need
  it and cannot refine the query.

## Consequences
- Changing the embedding model requires full re-ingestion; the embedding model id is stored
  with each segment and checked at startup.
