# ADR-0005: RAG over runbooks and postmortems with pgvector

- Status: Accepted
- Date: 2026-10-18

## Context
Diagnosis quality depends on organisational knowledge: runbooks per service and past
postmortems. Content changes rarely and is versioned in Git.

## Decision
- Knowledge source: Markdown files under `knowledge/` (runbooks, postmortems) with
  YAML front matter (`service`, `type`, `title`, `updated`).
- Explicit ingestion pipeline (not "easy RAG"): parse front matter, split by Markdown
  headings with a size limit, embed, store in pgvector with metadata. Ingestion is
  idempotent by content hash; changed documents replace their old segments.
- Ingestion runs on startup when enabled by config and via an admin-only REST endpoint.
- Retrieval: filtered by `service` when triage identified it, top-k with a minimum score,
  results passed to the diagnosis agent with source references. The agent must cite
  document titles in its evidence.
- Retrieved text is treated as untrusted (ADR-0007 guardrails).
- Same PostgreSQL instance as workflow state, separate table.

## Alternatives considered
- Dedicated vector DB (Qdrant, OpenSearch): rejected — unnecessary for this volume,
  one less component.
- Retrieval as a tool the agent calls vs. injected context: retrieval is exposed as a
  tool to the diagnosis agent so it can search iteratively, counted in its step budget.

## Consequences
- Embedding model change requires full re-ingestion; the embedding model id is stored
  with each segment and checked at startup.
