# A-0013: Knowledge base storage and ingestion

- Status: Accepted
- Date: 2026-10-25
- Applies: P-0005, P-0021, P-0008, P-0009, P-0016

## Context
A-0004 defines the knowledge base (`knowledge/`, front matter `service`, `type`, `title`,
`updated`, role `knowledge-admin`). The ingestion and retriever of INC-005 need a concrete
embedding model, tables, file location in each environment and an admin endpoint. The
diagnosis tool that uses the retriever follows with INC-007.

## Decision
- **Embedding model**: through quarkus-langchain4j with Ollama, model `nomic-embed-text`
  (768 dimensions) in all profiles; prod points to an Ollama endpoint by environment
  variable (`OLLAMA_BASE_URL`, required; no local default in prod). The stored model id is
  `ollama/nomic-embed-text`. Embedding calls have a timeout and retry (P-0008).
- **Local Ollama** (P-0016, A-0008): service `ollama` in `compose-devservices.yml`, image
  pinned, host port `11434`, models in volume `incident-assistant-ollama`; dev mode pulls
  the model on first use. Tests use a fake embedding model and no Ollama.
- **Tables** (P-0021, migration `V3__knowledge_base.sql`, `create extension vector`):
  - `knowledge_document`: `path` (unique, relative to the knowledge directory),
    `content_hash` (SHA-256 of the file), `embedding_model`, `service` (nullable: general
    documents), `doc_type` (`RUNBOOK|POSTMORTEM`), `title`, `updated` (date);
  - `knowledge_segment`: `document_id` (FK, cascade delete), `position`, `heading`,
    `text`, `embedding vector`.
- **Splitting**: by Markdown headings (`#` to `###`); a section longer than 1500
  characters is split at paragraph boundaries; front matter is not embedded. Each segment
  is embedded as `title — heading — text`.
- **Front matter** parsed as YAML (`jackson-dataformat-yaml`); a file without valid front
  matter, or with an unknown `type`, is skipped with a warning naming the path and the
  ingestion reports it.
- **Location**: config `incident.knowledge.directory`; dev and test `../knowledge`
  (repository root); prod required (the image or a volume provides it).
  `incident.knowledge.ingest-on-startup` (default `false`, dev `true`).
- **Admin endpoint**: `POST /api/v1/knowledge/ingestion`, role `knowledge-admin`, runs
  ingestion synchronously and returns counts (`added`, `updated`, `unchanged`, `removed`,
  `skipped` with paths). One run at a time per database (advisory lock); a concurrent
  request gets `409`. The local realm gets the role and a user `knowledge-admin`.
- **Retriever** (service layer): `search(query, service, limit, minScore)` returns
  segments with document title, path, heading and score; `service` filters to that
  service's documents plus general ones. Defaults: `limit` 5, `min-score` 0.6 (cosine
  similarity), configurable under `incident.knowledge.retrieval.*`.
- **Sample content**: one runbook per mock service of A-0005 (`checkout`, `payments`,
  `inventory`) and two postmortems matching the scenarios `bad-deployment` and
  `memory-leak`.

## Implementation (INC-005)
- The ingestion lock is a PostgreSQL session advisory lock held on a connection of its own
  for the whole run; embedding happens outside any transaction and each document is written
  in a short transaction. A missing knowledge directory fails the run and changes nothing,
  so a wrong path never empties the knowledge base.
- A file that is still present but no longer valid is reported as skipped and keeps its last
  good version; only files that are gone are removed.
- Search filters by the configured embedding model as well, so a half-finished
  re-embedding never mixes vectors of two models.
- Tests replace the embedding model with a deterministic bag-of-words fake (CDI alternative)
  and store the model id `test/fake-embedding`.
- Prod creates the `vector` extension in the migration; the database role needs the right
  to do so, or the extension is created beforehand by the database administrator.

## Alternatives considered
- OpenAI `text-embedding-3-small` in prod and Ollama in dev: better quality hosted, but
  two models, so dev results say little about prod, and a paid call per ingestion.
- Ollama Dev Services instead of a compose service: starts a container per dev session and
  pulls the model each time, against A-0008's kept services.
- Knowledge files packaged as classpath resources: no external directory, but every
  document change needs a new build of the service.

## Consequences
- Prod needs a reachable Ollama for embeddings even when chat uses a hosted provider.
- The retriever is not yet called by any step; INC-007 exposes it as a tool.
- Not in scope: editing documents through the API, per-user access to documents, PDF or
  other formats.
