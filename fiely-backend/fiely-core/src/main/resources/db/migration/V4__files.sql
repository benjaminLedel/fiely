-- V4__files.sql
-- Core file tree. A single table models both folders and files using a
-- self-referential parent_id. Binary content lives outside the database in a
-- StorageProvider plugin (e.g. fiely-storage-local); rows only carry the
-- metadata needed to resolve it — storage_id (which provider) and
-- storage_path (the opaque ref the provider returned on store).
--
-- The folder-XOR-blob CHECK constraint enforces the invariant that folders
-- never carry storage refs and files always do.

CREATE TABLE IF NOT EXISTS files (
    id              UUID         PRIMARY KEY,
    tenant_id       UUID         NOT NULL REFERENCES tenants(id)    ON DELETE CASCADE,
    owner_id        UUID         NOT NULL REFERENCES auth_users(id) ON DELETE CASCADE,
    parent_id       UUID         REFERENCES files(id)               ON DELETE CASCADE,
    name            VARCHAR(255) NOT NULL,
    is_folder       BOOLEAN      NOT NULL DEFAULT FALSE,
    size_bytes      BIGINT       NOT NULL DEFAULT 0,
    content_type    VARCHAR(255),
    storage_id      VARCHAR(64),
    storage_path    TEXT,
    current_version INT          NOT NULL DEFAULT 1,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT files_folder_xor_blob CHECK (
        (is_folder =  TRUE AND storage_id IS NULL     AND storage_path IS NULL)
     OR (is_folder = FALSE AND storage_id IS NOT NULL AND storage_path IS NOT NULL)
    ),
    CONSTRAINT files_unique_name_per_parent UNIQUE (owner_id, parent_id, name)
);

-- Postgres treats NULLs as distinct in UNIQUE, so the compound constraint above
-- doesn't catch sibling collisions at the root. A partial unique index does.
CREATE UNIQUE INDEX IF NOT EXISTS files_unique_root_name
    ON files (owner_id, name) WHERE parent_id IS NULL;

CREATE INDEX IF NOT EXISTS files_tenant_idx ON files (tenant_id);
CREATE INDEX IF NOT EXISTS files_owner_parent_idx ON files (owner_id, parent_id);
