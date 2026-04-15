-- V5__file_metadata.sql
-- Namespaced metadata for files. Each file can carry multiple metadata
-- documents, one per namespace — `user` for caller-supplied tags/ratings,
-- `exif`/`pdf` etc. for extractors, `fiely-ai-*` for model outputs. This
-- keeps producers from stepping on each other.
--
-- `data` is TEXT holding a JSON document. Migrating to JSONB later is a
-- one-liner (`ALTER ... TYPE JSONB USING data::jsonb`) once we need GIN
-- indexing — the test matrix currently includes H2 which doesn't speak
-- JSONB, so TEXT keeps the application portable.

CREATE TABLE IF NOT EXISTS file_metadata (
    file_id    UUID         NOT NULL REFERENCES files(id) ON DELETE CASCADE,
    namespace  VARCHAR(64)  NOT NULL,
    data       TEXT         NOT NULL,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    PRIMARY KEY (file_id, namespace)
);

CREATE INDEX IF NOT EXISTS file_metadata_namespace_idx ON file_metadata (namespace);
