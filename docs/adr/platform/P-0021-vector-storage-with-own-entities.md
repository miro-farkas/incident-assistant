# P-0021: Vector storage with own entities and an explicit model check

- Status: Accepted
- Date: 2026-10-25
- Amends: P-0005 (storage of segments, embedding model check)

## Context
P-0005 puts embeddings into pgvector in the application's database and requires idempotent
ingestion and a stored embedding model id. Ready-made embedding store integrations manage
their own table, create it at runtime, run their own SQL and keep metadata in a JSON
column. That bypasses the schema ownership of Flyway, the entity conventions (P-0014) and
the rule that only repositories query (P-0015). The embedding model, and with it the
vector dimension, differs between environments and may change.

## Decision
- Two entities in `model`, tables created by a migration: a **document** per source file
  (path, content hash, embedding model id, metadata used for filtering and citation) and
  its **segments** (text, position, embedding). Both extend `BaseEntity`.
- The embedding column is `vector` without a fixed dimension, mapped with Hibernate's
  vector support (`hibernate-vector`). Search is exact (no approximate index); an index is
  added by migration once a fixed model and a volume that needs it exist.
- Similarity search is a repository method (cosine distance, top-k, minimum score,
  metadata filters as `where` clauses); nothing outside `repository` issues it.
- The AI framework is used for the embedding model only. Splitting, hashing and storing
  are application code.
- Idempotency per document: same path and content hash and model id → skipped; otherwise
  its segments are replaced in one transaction; documents whose file is gone are deleted.
- Model check at startup: if stored documents were embedded with another model id than
  the configured one, startup fails with a message naming both, unless ingestion on
  startup is enabled, in which case every document is re-embedded. Results of different
  models are never mixed.

## Alternatives considered
- Framework embedding store (own table, created at runtime): least code, but outside
  Flyway and P-0014/P-0015, and metadata only as JSON.
- Fixed-dimension column with an HNSW index: faster at volume, but every model change
  becomes a migration, and the volume does not need it.
- Re-embedding automatically on every model mismatch: hides a configuration mistake and
  can cost a full re-ingestion against a paid provider on a wrong deployment.

## Consequences
- Exact search scans all segments of the filtered documents; fine for thousands of
  segments, to be revisited with an index beyond that.
- Changing the model needs a re-ingestion run (startup flag or admin endpoint), as P-0005
  already states.
- Not in scope: hybrid (keyword + vector) search, re-ranking.
