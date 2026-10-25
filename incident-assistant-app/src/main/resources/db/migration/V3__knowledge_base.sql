-- Knowledge base (A-0004, A-0013, P-0021): one row per Markdown file of the knowledge
-- directory and its embedded segments. The embedding column has no fixed dimension, so a
-- change of the embedding model needs a re-ingestion, not a migration; search is exact.

create extension if not exists vector;

create table knowledge_document (
    id              uuid          not null,
    created_at      timestamptz   not null,
    updated_at      timestamptz   not null,
    version         bigint        not null,
    path            varchar(500)  not null,
    content_hash    varchar(64)   not null,
    embedding_model varchar(200)  not null,
    service         varchar(100),
    doc_type        varchar(16)   not null,
    title           varchar(300)  not null,
    updated         date          not null,
    constraint pk_knowledge_document primary key (id),
    constraint uq_knowledge_document_path unique (path),
    constraint ck_knowledge_document_type check (doc_type in ('RUNBOOK', 'POSTMORTEM'))
);

create index ix_knowledge_document_service on knowledge_document (service);

-- Replaced as a whole when its document changes. Append-only otherwise.
create table knowledge_segment (
    id          uuid          not null,
    created_at  timestamptz   not null,
    updated_at  timestamptz   not null,
    version     bigint        not null,
    document_id uuid          not null,
    position    integer       not null,
    heading     varchar(300),
    text        text          not null,
    embedding   vector        not null,
    constraint pk_knowledge_segment primary key (id),
    constraint fk_knowledge_segment_document foreign key (document_id)
        references knowledge_document (id) on delete cascade,
    constraint uq_knowledge_segment_position unique (document_id, position)
);
